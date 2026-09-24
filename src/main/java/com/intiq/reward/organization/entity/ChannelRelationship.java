package com.intiq.reward.organization.entity;

import com.intiq.reward.common.entity.AuditableEntity;
import com.intiq.reward.organization.enums.ChannelStatus;
import com.intiq.reward.organization.enums.PartnerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One brand to partner trading link: "this distributor or retailer sells this brand".
 * From MVP 2, every "can this partner earn from this brand's scheme?" check reads this row,
 * which is also what keeps one brand's partner list invisible to another brand.
 */
@Entity
@Table(name = "channel_relationships")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChannelRelationship extends AuditableEntity {

    @Column(name = "brand_org_id", nullable = false, updatable = false)
    private UUID brandOrgId;

    @Column(name = "partner_org_id", nullable = false, updatable = false)
    private UUID partnerOrgId;

    @Enumerated(EnumType.STRING)
    @Column(name = "partner_type", nullable = false, length = 16, updatable = false)
    private PartnerType partnerType;

    /** Distributor supplying this retailer for this brand. Null for a distributor, or direct supply. */
    @Column(name = "parent_org_id")
    private UUID parentOrgId;

    @Column(name = "region", length = 50)
    private String region;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ChannelStatus status;

    @Column(name = "joined_at")
    private Instant joinedAt;

    public static ChannelRelationship link(UUID brandOrgId,
                                           UUID partnerOrgId,
                                           PartnerType partnerType,
                                           UUID parentOrgId,
                                           String region,
                                           Instant now) {
        if (brandOrgId.equals(partnerOrgId)) {
            throw new IllegalArgumentException("A brand cannot be its own partner");
        }
        if (parentOrgId != null && partnerType != PartnerType.RETAILER) {
            throw new IllegalArgumentException("Only a retailer can sit under a distributor");
        }
        ChannelRelationship relationship = new ChannelRelationship();
        relationship.brandOrgId = brandOrgId;
        relationship.partnerOrgId = partnerOrgId;
        relationship.partnerType = partnerType;
        relationship.parentOrgId = parentOrgId;
        relationship.region = region;
        relationship.status = ChannelStatus.ACTIVE;
        relationship.joinedAt = now;
        return relationship;
    }

    public void reassignParent(UUID distributorOrgId) {
        if (partnerType != PartnerType.RETAILER) {
            throw new IllegalStateException("Only a retailer can sit under a distributor");
        }
        this.parentOrgId = distributorOrgId;
    }

    public void updateRegion(String region) {
        this.region = region;
    }

    public void suspend() {
        if (status != ChannelStatus.ACTIVE) {
            throw new IllegalStateException("Only an active relationship can be suspended");
        }
        this.status = ChannelStatus.SUSPENDED;
    }

    public void reactivate() {
        if (status != ChannelStatus.SUSPENDED) {
            throw new IllegalStateException("Only a suspended relationship can be reactivated");
        }
        this.status = ChannelStatus.ACTIVE;
    }

    public void end() {
        this.status = ChannelStatus.ENDED;
    }

    public boolean isActive() {
        return status == ChannelStatus.ACTIVE;
    }
}
