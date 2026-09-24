package com.intiq.reward.organization.service;

import com.intiq.reward.common.enums.ActorType;
import com.intiq.reward.common.exception.DomainException;
import com.intiq.reward.common.exception.ErrorCode;
import com.intiq.reward.organization.entity.Organization;
import com.intiq.reward.organization.enums.ChannelStatus;
import com.intiq.reward.organization.enums.OrgStatus;
import com.intiq.reward.organization.enums.OrgType;
import com.intiq.reward.organization.repository.ChannelRelationshipRepository;
import com.intiq.reward.organization.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * The only class other modules use from {@code organization}. It hands back a small read-only
 * summary rather than the entity, which is what keeps organisation tables out of everyone else.
 */
@Service
@RequiredArgsConstructor
public class OrgDirectoryService {

    private final OrganizationRepository organizationRepository;
    private final ChannelRelationshipRepository channelRelationshipRepository;

    @Transactional(readOnly = true)
    public Optional<OrgSummary> find(UUID orgId) {
        return organizationRepository.findById(orgId).map(OrgSummary::from);
    }

    /** For callers that cannot proceed without a usable organisation. */
    @Transactional(readOnly = true)
    public OrgSummary requireActive(UUID orgId) {
        OrgSummary summary = find(orgId).orElseThrow(() -> new DomainException(ErrorCode.ORG_NOT_FOUND));
        if (summary.status() != OrgStatus.ACTIVE) {
            throw new DomainException(ErrorCode.ORG_NOT_ACTIVE);
        }
        return summary;
    }

    /** The eligibility check MVP 2 earning rules will call on every claim. */
    @Transactional(readOnly = true)
    public boolean isActivePartner(UUID brandOrgId, UUID partnerOrgId) {
        return channelRelationshipRepository
                .existsByBrandOrgIdAndPartnerOrgIdAndStatus(brandOrgId, partnerOrgId, ChannelStatus.ACTIVE);
    }

    /**
     * What a login acts as, derived from the business it belongs to. The platform organisation maps
     * to ADMIN because that is what it is in every other module's language.
     */
    public static ActorType actorTypeFor(OrgType orgType) {
        return switch (orgType) {
            case PLATFORM -> ActorType.ADMIN;
            case BRAND -> ActorType.BRAND;
            case DISTRIBUTOR -> ActorType.DISTRIBUTOR;
            case RETAILER -> ActorType.RETAILER;
        };
    }

    public record OrgSummary(UUID id,
                             String code,
                             String displayName,
                             OrgType orgType,
                             OrgStatus status,
                             ActorType actorType) {

        static OrgSummary from(Organization organization) {
            return new OrgSummary(organization.getId(),
                    organization.getCode(),
                    organization.getDisplayName(),
                    organization.getOrgType(),
                    organization.getStatus(),
                    actorTypeFor(organization.getOrgType()));
        }
    }
}
