package com.intiq.reward.organization.dto.response;

import com.intiq.reward.organization.enums.KycStatus;
import com.intiq.reward.organization.enums.OrgStatus;
import com.intiq.reward.organization.enums.OrgType;

import java.time.Instant;
import java.util.UUID;

/** panMasked, never the PAN itself: the stored value is decrypted and masked in the service. */
public record OrganizationResponse(UUID id,
                                   String code,
                                   OrgType orgType,
                                   String legalName,
                                   String displayName,
                                   String gstin,
                                   String panMasked,
                                   OrgStatus status,
                                   KycStatus kycStatus,
                                   String logoUrl,
                                   OrganizationContactResponse contact,
                                   Instant createdAt) {
}
