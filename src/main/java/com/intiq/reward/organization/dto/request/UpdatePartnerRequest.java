package com.intiq.reward.organization.dto.request;

import jakarta.validation.constraints.Size;

import java.util.UUID;

/** What a brand may change about its link to a partner, not about the partner profile itself. */
public record UpdatePartnerRequest(UUID parentOrgId, @Size(max = 50) String region) {
}
