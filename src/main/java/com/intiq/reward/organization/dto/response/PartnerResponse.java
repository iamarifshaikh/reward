package com.intiq.reward.organization.dto.response;

import com.intiq.reward.organization.enums.ChannelStatus;
import com.intiq.reward.organization.enums.PartnerType;

import java.time.Instant;
import java.util.UUID;

/** One row of a brand partner list: the relationship, plus enough of the partner to display it. */
public record PartnerResponse(UUID relationshipId,
                              UUID partnerOrgId,
                              String partnerCode,
                              String partnerName,
                              PartnerType partnerType,
                              ChannelStatus status,
                              UUID parentOrgId,
                              String parentName,
                              String region,
                              String contactPhone,
                              Instant joinedAt) {
}
