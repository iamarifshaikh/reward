package com.intiq.reward.organization.service;

import com.intiq.reward.audit.constant.AuditActions;
import com.intiq.reward.audit.service.AuditService;
import com.intiq.reward.auth.service.AuthUserService;
import com.intiq.reward.common.exception.DomainException;
import com.intiq.reward.common.exception.ErrorCode;
import com.intiq.reward.common.security.AuthPrincipal;
import com.intiq.reward.common.util.ContactUtils;
import com.intiq.reward.common.util.Masking;
import com.intiq.reward.common.util.PiiEncryptor;
import com.intiq.reward.organization.dto.request.CreateBrandRequest;
import com.intiq.reward.organization.dto.request.CreatePartnerRequest;
import com.intiq.reward.organization.dto.request.CreateRetailerRequest;
import com.intiq.reward.organization.dto.request.UpdatePendingPartnerRequest;
import com.intiq.reward.organization.dto.response.BrandCardResponse;
import com.intiq.reward.organization.dto.response.OrganizationContactResponse;
import com.intiq.reward.organization.dto.response.OrganizationResponse;
import com.intiq.reward.organization.dto.response.OrganizationSummaryResponse;
import com.intiq.reward.organization.entity.ChannelRelationship;
import com.intiq.reward.organization.entity.Organization;
import com.intiq.reward.organization.enums.ChannelStatus;
import com.intiq.reward.organization.enums.OrgStatus;
import com.intiq.reward.organization.enums.OrgType;
import com.intiq.reward.organization.enums.PartnerType;
import com.intiq.reward.organization.repository.ChannelRelationshipRepository;
import com.intiq.reward.organization.repository.ChannelRelationshipRepository.PartnerCountRow;
import com.intiq.reward.organization.repository.OrganizationRepository;
import com.intiq.reward.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Creating a brand writes two things in one transaction: the organisation, and the single login it
 * gets to act as. Both succeed together or neither does — a brand row with no way to log in, or a
 * login with no organisation behind it, would each be a broken half-state.
 */
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final ChannelRelationshipRepository channelRelationshipRepository;
    private final AuthUserService authUserService;
    private final OrgCodeGenerator codeGenerator;
    private final PiiEncryptor piiEncryptor;
    private final AuditService auditService;
    private final Clock clock;

    /**
     * A caller can send no sort, a blank one (a real quirk: some clients send {@code sort=} as an
     * empty value rather than omitting it, which Spring Data treats as "sort by nothing" rather
     * than "no preference"), or a made-up field name. All three fall back to this default instead
     * of reaching the database, rather than failing outright.
     */
    private static final Set<String> BRAND_SORT_PROPERTIES = Set.of("code", "displayName", "createdAt", "status", "kycStatus");
    private static final Sort DEFAULT_BRAND_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    /** Oldest first by default — this is a queue, and nothing should sit at the bottom forever. */
    private static final Set<String> PENDING_SORT_PROPERTIES = Set.of("code", "displayName", "createdAt", "orgType");
    private static final Sort DEFAULT_PENDING_SORT = Sort.by(Sort.Direction.ASC, "createdAt");

    /**
     * A brand created by the platform admin needs no separate approval step — the admin's own
     * action is the approval, which is why {@link Organization#approve} is called immediately
     * rather than leaving it at {@code PENDING_APPROVAL}.
     */
    @Transactional
    public OrganizationResponse createBrand(CreateBrandRequest request, AuthPrincipal admin) {
        String phone = ContactUtils.normalizePhone(request.ownerPhone());
        String email = ContactUtils.normalizeEmail(request.ownerEmail());
        if (phone == null && email == null) {
            throw new DomainException(ErrorCode.USER_CONTACT_REQUIRED);
        }
        if (authUserService.contactExists(phone, email)) {
            throw new DomainException(ErrorCode.USER_CONTACT_TAKEN);
        }
        if (request.gstin() != null && organizationRepository.existsByGstin(request.gstin())) {
            throw new DomainException(ErrorCode.ORG_GSTIN_DUPLICATE);
        }

        String panHash = request.pan() != null ? piiEncryptor.hash(request.pan()) : null;
        if (panHash != null && organizationRepository.existsByPanHash(panHash)) {
            throw new DomainException(ErrorCode.ORG_PAN_DUPLICATE);
        }

        String code = codeGenerator.next(OrgType.BRAND);
        Organization org = Organization.create(OrgType.BRAND, code, request.legalName(), request.displayName(),
                OrgStatus.PENDING_APPROVAL);
        org.updateProfile(request.legalName(), request.displayName(), request.gstin());
        if (panHash != null) {
            org.updatePan(piiEncryptor.encrypt(request.pan()), panHash);
        }
        org.approve(admin.userId(), clock.instant());
        org = organizationRepository.save(org);

        AuthUserService.Summary contact = authUserService.createBusinessLogin(org.getId(), phone, email, request.ownerName());

        auditService.record(AuditActions.ORG_CREATED, AuditActions.ENTITY_ORGANIZATION, org.getId(), org.getId(),
                Map.of("orgType", OrgType.BRAND.name(), "code", code, "displayName", request.displayName()));

        return toResponse(org, contact);
    }

    /**
     * A brand creating its own distributor — the only partner type a brand may create directly.
     * Unlike {@link #createBrand}, this does <em>not</em> approve the organisation — it stays
     * {@code PENDING_APPROVAL} until an admin reviews it, which is also what keeps the new login
     * unable to sign in in the meantime (login already refuses any organisation that is not
     * {@code ACTIVE}). A retailer is {@link #createRetailer}, a distributor's own action.
     */
    @Transactional
    public OrganizationResponse createPartner(CreatePartnerRequest request, AuthPrincipal brand) {
        UUID brandOrgId = brand.orgId();
        String phone = ContactUtils.normalizePhone(request.ownerPhone());
        String email = ContactUtils.normalizeEmail(request.ownerEmail());
        if (phone == null && email == null) {
            throw new DomainException(ErrorCode.USER_CONTACT_REQUIRED);
        }
        if (authUserService.contactExists(phone, email)) {
            throw new DomainException(ErrorCode.USER_CONTACT_TAKEN);
        }
        if (request.gstin() != null && organizationRepository.existsByGstin(request.gstin())) {
            throw new DomainException(ErrorCode.ORG_GSTIN_DUPLICATE);
        }
        String panHash = request.pan() != null ? piiEncryptor.hash(request.pan()) : null;
        if (panHash != null && organizationRepository.existsByPanHash(panHash)) {
            throw new DomainException(ErrorCode.ORG_PAN_DUPLICATE);
        }

        String code = codeGenerator.next(OrgType.DISTRIBUTOR);
        Organization org = Organization.create(OrgType.DISTRIBUTOR, code, request.legalName(), request.displayName(),
                OrgStatus.PENDING_APPROVAL);
        org.updateProfile(request.legalName(), request.displayName(), request.gstin());
        if (panHash != null) {
            org.updatePan(piiEncryptor.encrypt(request.pan()), panHash);
        }
        org = organizationRepository.save(org);

        AuthUserService.Summary contact = authUserService.createBusinessLogin(org.getId(), phone, email, request.ownerName());

        channelRelationshipRepository.save(ChannelRelationship.link(brandOrgId, org.getId(), PartnerType.DISTRIBUTOR,
                null, request.region(), clock.instant()));

        auditService.record(AuditActions.PARTNER_CREATED, AuditActions.ENTITY_ORGANIZATION, org.getId(), brandOrgId,
                Map.of("partnerType", PartnerType.DISTRIBUTOR.name(), "code", code, "displayName", request.displayName()));

        return toResponse(org, contact);
    }

    /**
     * A brand correcting a distributor it created, while it is still awaiting a decision or was
     * rejected — GSTIN typo, wrong phone number, whatever admin flagged. Once approved, this
     * refuses: an active organisation's profile changes through a different, audited path, not by
     * quietly editing what already passed review.
     */
    @Transactional
    public OrganizationResponse updatePartner(UUID orgId, UpdatePendingPartnerRequest request, AuthPrincipal brand) {
        Organization org = requireEditablePartner(brand.orgId(), orgId);

        if (request.gstin() != null && !request.gstin().equals(org.getGstin())
                && organizationRepository.existsByGstin(request.gstin())) {
            throw new DomainException(ErrorCode.ORG_GSTIN_DUPLICATE);
        }
        String panHash = request.pan() != null ? piiEncryptor.hash(request.pan()) : null;
        if (panHash != null && !panHash.equals(org.getPanHash()) && organizationRepository.existsByPanHash(panHash)) {
            throw new DomainException(ErrorCode.ORG_PAN_DUPLICATE);
        }
        org.updateProfile(request.legalName(), request.displayName(), request.gstin());
        if (panHash != null) {
            org.updatePan(piiEncryptor.encrypt(request.pan()), panHash);
        }

        String phone = ContactUtils.normalizePhone(request.ownerPhone());
        String email = ContactUtils.normalizeEmail(request.ownerEmail());
        AuthUserService.Summary contact =
                authUserService.correctBusinessContact(orgId, phone, email, request.ownerName());

        channelRelationshipRepository.findByBrandOrgIdAndPartnerOrgId(brand.orgId(), orgId)
                .ifPresent(link -> link.updateRegion(request.region()));

        auditService.record(AuditActions.ORG_UPDATED, AuditActions.ENTITY_ORGANIZATION, orgId, brand.orgId(),
                Map.of("code", org.getCode(), "displayName", request.displayName()));

        return toResponse(org, contact);
    }

    /** Puts a rejected distributor back in the admin queue, unchanged apart from its status. */
    @Transactional
    public void resubmitPartner(UUID orgId, AuthPrincipal brand) {
        Organization org = requireBrandOwnsDistributor(brand.orgId(), orgId);
        if (org.getStatus() != OrgStatus.REJECTED) {
            throw new DomainException(ErrorCode.ORG_INVALID_STATE);
        }
        org.resubmit();
        auditService.record(AuditActions.ORG_RESUBMITTED, AuditActions.ENTITY_ORGANIZATION, orgId, brand.orgId(),
                Map.of("code", org.getCode()));
    }

    private Organization requireEditablePartner(UUID brandOrgId, UUID orgId) {
        Organization org = requireBrandOwnsDistributor(brandOrgId, orgId);
        if (org.getStatus() != OrgStatus.PENDING_APPROVAL && org.getStatus() != OrgStatus.REJECTED) {
            throw new DomainException(ErrorCode.ORG_INVALID_STATE);
        }
        return org;
    }

    /** 404s rather than 403s on someone else's org id, so ids cannot be probed. */
    private Organization requireBrandOwnsDistributor(UUID brandOrgId, UUID orgId) {
        channelRelationshipRepository.findByBrandOrgIdAndPartnerOrgId(brandOrgId, orgId)
                .filter(link -> link.getPartnerType() == PartnerType.DISTRIBUTOR)
                .orElseThrow(() -> new DomainException(ErrorCode.ORG_NOT_FOUND));
        return organizationRepository.findById(orgId).orElseThrow(() -> new DomainException(ErrorCode.ORG_NOT_FOUND));
    }

    /**
     * A distributor creating a retailer under itself, for one of the brands it supplies. Unlike a
     * brand creating a distributor, this needs no admin review — the retailer is approved in the
     * same call and can sign in right away, the same way {@link #createBrand} auto-approves.
     */
    @Transactional
    public OrganizationResponse createRetailer(CreateRetailerRequest request, AuthPrincipal distributor) {
        UUID distributorOrgId = distributor.orgId();
        ChannelRelationship supplyLink = channelRelationshipRepository
                .findByBrandOrgIdAndPartnerOrgId(request.brandOrgId(), distributorOrgId)
                .filter(link -> link.getPartnerType() == PartnerType.DISTRIBUTOR && link.isActive())
                .orElseThrow(() -> new DomainException(ErrorCode.PARTNER_NOT_LINKED));

        String phone = ContactUtils.normalizePhone(request.ownerPhone());
        String email = ContactUtils.normalizeEmail(request.ownerEmail());
        if (phone == null && email == null) {
            throw new DomainException(ErrorCode.USER_CONTACT_REQUIRED);
        }
        if (authUserService.contactExists(phone, email)) {
            throw new DomainException(ErrorCode.USER_CONTACT_TAKEN);
        }
        if (request.gstin() != null && organizationRepository.existsByGstin(request.gstin())) {
            throw new DomainException(ErrorCode.ORG_GSTIN_DUPLICATE);
        }
        String panHash = request.pan() != null ? piiEncryptor.hash(request.pan()) : null;
        if (panHash != null && organizationRepository.existsByPanHash(panHash)) {
            throw new DomainException(ErrorCode.ORG_PAN_DUPLICATE);
        }

        String code = codeGenerator.next(OrgType.RETAILER);
        Organization org = Organization.create(OrgType.RETAILER, code, request.legalName(), request.displayName(),
                OrgStatus.PENDING_APPROVAL);
        org.updateProfile(request.legalName(), request.displayName(), request.gstin());
        if (panHash != null) {
            org.updatePan(piiEncryptor.encrypt(request.pan()), panHash);
        }
        org.approve(distributor.userId(), clock.instant());
        org = organizationRepository.save(org);

        AuthUserService.Summary contact = authUserService.createBusinessLogin(org.getId(), phone, email, request.ownerName());

        channelRelationshipRepository.save(ChannelRelationship.link(supplyLink.getBrandOrgId(), org.getId(),
                PartnerType.RETAILER, distributorOrgId, request.region(), clock.instant()));

        auditService.record(AuditActions.PARTNER_CREATED, AuditActions.ENTITY_ORGANIZATION, org.getId(),
                request.brandOrgId(), Map.of("partnerType", PartnerType.RETAILER.name(), "code", code,
                        "displayName", request.displayName(), "parentOrgId", distributorOrgId.toString()));

        return toResponse(org, contact);
    }

    /** The admin's to-do list: every organisation still waiting on a decision, any type. */
    @Transactional(readOnly = true)
    public PageResponse<OrganizationSummaryResponse> listPending(Pageable pageable) {
        Specification<Organization> pendingOnly =
                (root, query, cb) -> cb.equal(root.get("status"), OrgStatus.PENDING_APPROVAL);
        Page<Organization> page = organizationRepository.findAll(pendingOnly, sanitizePendingSort(pageable));

        List<UUID> orgIds = page.getContent().stream().map(Organization::getId).toList();
        Map<UUID, AuthUserService.Summary> contacts = authUserService.findByOrgIds(orgIds);

        return PageResponse.of(page, org -> {
            AuthUserService.Summary contact = contacts.get(org.getId());
            return new OrganizationSummaryResponse(
                    org.getId(), org.getCode(), org.getOrgType(), org.getDisplayName(), org.getStatus(), org.getKycStatus(),
                    contact == null ? null : contact.fullName(),
                    contact == null ? null : contact.phone(),
                    contact == null ? null : contact.email());
        });
    }

    @Transactional
    public void approveOrganization(UUID orgId, AuthPrincipal admin) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new DomainException(ErrorCode.ORG_NOT_FOUND));
        if (org.getStatus() != OrgStatus.PENDING_APPROVAL) {
            throw new DomainException(ErrorCode.ORG_INVALID_STATE);
        }
        org.approve(admin.userId(), clock.instant());
        auditService.record(AuditActions.ORG_APPROVED, AuditActions.ENTITY_ORGANIZATION, org.getId(), org.getId(),
                Map.of("orgType", org.getOrgType().name(), "code", org.getCode()));
    }

    /** Remarks go to the audit trail only — there is no rejection-reason column on the organisation. */
    @Transactional
    public void rejectOrganization(UUID orgId, String remarks, AuthPrincipal admin) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new DomainException(ErrorCode.ORG_NOT_FOUND));
        if (org.getStatus() != OrgStatus.PENDING_APPROVAL) {
            throw new DomainException(ErrorCode.ORG_INVALID_STATE);
        }
        org.reject();
        auditService.record(AuditActions.ORG_REJECTED, AuditActions.ENTITY_ORGANIZATION, org.getId(), org.getId(),
                Map.of("orgType", org.getOrgType().name(), "code", org.getCode(), "remarks", remarks == null ? "" : remarks));
    }

    private Pageable sanitizePendingSort(Pageable pageable) {
        List<Sort.Order> validOrders = pageable.getSort().stream()
                .filter(order -> PENDING_SORT_PROPERTIES.contains(order.getProperty()))
                .toList();
        Sort sort = validOrders.isEmpty() ? DEFAULT_PENDING_SORT : Sort.by(validOrders);
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    /**
     * The admin brand list. One query for the page of brands, one grouped query for every brand's
     * distributor and retailer counts together — the count never costs more than those two queries,
     * no matter how many brands are on the page.
     */
    @Transactional(readOnly = true)
    public PageResponse<BrandCardResponse> listBrands(Pageable pageable) {
        Page<Organization> brands = organizationRepository.findByOrgType(OrgType.BRAND, sanitizeSort(pageable));
        List<UUID> brandIds = brands.getContent().stream().map(Organization::getId).toList();

        Map<UUID, Long> distributorCounts = new HashMap<>();
        Map<UUID, Long> retailerCounts = new HashMap<>();
        if (!brandIds.isEmpty()) {
            for (PartnerCountRow row : channelRelationshipRepository.countPartnersByBrandIds(brandIds, ChannelStatus.ACTIVE)) {
                Map<UUID, Long> target = row.getPartnerType() == PartnerType.DISTRIBUTOR ? distributorCounts : retailerCounts;
                target.put(row.getBrandOrgId(), row.getTotal());
            }
        }

        return PageResponse.of(brands, org -> new BrandCardResponse(
                org.getId(),
                org.getCode(),
                org.getDisplayName(),
                null, // no file-upload module yet; frontend falls back to an initials avatar
                org.getStatus(),
                org.getKycStatus(),
                distributorCounts.getOrDefault(org.getId(), 0L),
                retailerCounts.getOrDefault(org.getId(), 0L)));
    }

    /**
     * Keeps only sort instructions that name a real, whitelisted column. An empty or unrecognised
     * sort falls back to newest-first rather than reaching the database at all — a blank property
     * name is exactly what a bare {@code ?sort=} produces, and Spring Data would otherwise crash
     * trying to sort by a column that does not exist.
     */
    private Pageable sanitizeSort(Pageable pageable) {
        List<Sort.Order> validOrders = pageable.getSort().stream()
                .filter(order -> BRAND_SORT_PROPERTIES.contains(order.getProperty()))
                .toList();
        Sort sort = validOrders.isEmpty() ? DEFAULT_BRAND_SORT : Sort.by(validOrders);
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    private OrganizationResponse toResponse(Organization org, AuthUserService.Summary contact) {
        String panMasked = org.getPanHash() != null ? Masking.pan(unmaskedPanFor(org)) : null;
        OrganizationContactResponse contactResponse = new OrganizationContactResponse(
                contact.userId(), contact.fullName(), contact.phone(), contact.email(), contact.status());

        return new OrganizationResponse(
                org.getId(), org.getCode(), org.getOrgType(), org.getLegalName(), org.getDisplayName(),
                org.getGstin(), panMasked, org.getStatus(), org.getKycStatus(), org.getLogoObjectKey(),
                contactResponse, org.getCreatedAt());
    }

    /** Decrypts only long enough to mask; the plaintext PAN never leaves this method. */
    private String unmaskedPanFor(Organization org) {
        return piiEncryptor.decrypt(org.getPanEnc());
    }
}
