package com.intiq.reward.organization.repository;

import com.intiq.reward.organization.entity.ChannelRelationship;
import com.intiq.reward.organization.enums.ChannelStatus;
import com.intiq.reward.organization.enums.PartnerType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChannelRelationshipRepository extends JpaRepository<ChannelRelationship, UUID>,
                                                       JpaSpecificationExecutor<ChannelRelationship> {

    Optional<ChannelRelationship> findByBrandOrgIdAndPartnerOrgId(UUID brandOrgId, UUID partnerOrgId);

    boolean existsByBrandOrgIdAndPartnerOrgId(UUID brandOrgId, UUID partnerOrgId);

    /** The eligibility check every MVP 2 earning rule will call. */
    boolean existsByBrandOrgIdAndPartnerOrgIdAndStatus(UUID brandOrgId, UUID partnerOrgId, ChannelStatus status);

    /** A brand's partner list. Uses ix_channel__brand. */
    Page<ChannelRelationship> findByBrandOrgIdAndStatus(UUID brandOrgId, ChannelStatus status, Pageable pageable);

    Page<ChannelRelationship> findByBrandOrgIdAndPartnerTypeAndStatus(UUID brandOrgId,
                                                                     PartnerType partnerType,
                                                                     ChannelStatus status,
                                                                     Pageable pageable);

    /** The brands a partner is enrolled with. Uses ix_channel__partner. */
    List<ChannelRelationship> findByPartnerOrgIdAndStatus(UUID partnerOrgId, ChannelStatus status);

    /** Retailers sitting under one distributor for a brand. */
    List<ChannelRelationship> findByParentOrgIdAndStatus(UUID parentOrgId, ChannelStatus status);

    long countByBrandOrgIdAndStatus(UUID brandOrgId, ChannelStatus status);

    /**
     * Distributor and retailer counts for every brand in one query, instead of two count queries
     * per brand — a 20-brand list page costs one extra query total, not forty.
     */
    @Query("select c.brandOrgId as brandOrgId, c.partnerType as partnerType, count(c) as total "
            + "from ChannelRelationship c "
            + "where c.brandOrgId in :brandOrgIds and c.status = :status "
            + "group by c.brandOrgId, c.partnerType")
    List<PartnerCountRow> countPartnersByBrandIds(@Param("brandOrgIds") Collection<UUID> brandOrgIds,
                                                  @Param("status") ChannelStatus status);

    /** One row of the grouped count above: how many of one partner type one brand has. */
    interface PartnerCountRow {
        UUID getBrandOrgId();
        PartnerType getPartnerType();
        long getTotal();
    }
}
