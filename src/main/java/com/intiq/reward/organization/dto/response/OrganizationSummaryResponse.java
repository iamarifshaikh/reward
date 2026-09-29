package com.intiq.reward.organization.dto.response;

import com.intiq.reward.organization.enums.KycStatus;
import com.intiq.reward.organization.enums.OrgStatus;
import com.intiq.reward.organization.enums.OrgType;

import java.util.UUID;

/** Row shape for list screens: no PAN, but the owner's name/phone/email so an admin can review it. */
public record OrganizationSummaryResponse(UUID id,
                                          String code,
                                          OrgType orgType,
                                          String displayName,
                                          OrgStatus status,
                                          KycStatus kycStatus,
                                          String ownerName,
                                          String ownerPhone,
                                          String ownerEmail) {
}
