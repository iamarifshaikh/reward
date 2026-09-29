package com.intiq.reward.organization.repository;

import com.intiq.reward.organization.entity.Organization;
import com.intiq.reward.organization.enums.KycStatus;
import com.intiq.reward.organization.enums.OrgStatus;
import com.intiq.reward.organization.enums.OrgType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

/**
 * Specification support is here because the admin organisation list filters by any combination of
 * type, status, KYC state and free text, which would otherwise mean a query method per combination.
 */
public interface OrganizationRepository extends JpaRepository<Organization, UUID>,
                                                JpaSpecificationExecutor<Organization> {

    Optional<Organization> findByCode(String code);

    Optional<Organization> findByGstin(String gstin);

    boolean existsByGstin(String gstin);

    boolean existsByPanHash(String panHash);

    /** There is exactly one platform organisation. */
    Optional<Organization> findFirstByOrgType(OrgType orgType);

    Page<Organization> findByOrgTypeAndStatus(OrgType orgType, OrgStatus status, Pageable pageable);

    /** The admin brand list shows every status side by side (active, pending, suspended). */
    Page<Organization> findByOrgType(OrgType orgType, Pageable pageable);

    /** Admin KYC queue. Backed by the partial index on kyc_status = 'SUBMITTED'. */
    Page<Organization> findByKycStatus(KycStatus kycStatus, Pageable pageable);

    /** Uses the trigram index on display_name. */
    Page<Organization> findByDisplayNameContainingIgnoreCase(String text, Pageable pageable);
}
