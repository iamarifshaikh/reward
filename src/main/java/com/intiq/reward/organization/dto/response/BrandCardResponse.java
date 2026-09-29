package com.intiq.reward.organization.dto.response;

import com.intiq.reward.organization.enums.KycStatus;
import com.intiq.reward.organization.enums.OrgStatus;

import java.util.UUID;

/**
 * One card in the admin brand list. {@code logoUrl} is null until a file-upload module exists —
 * the frontend falls back to an initials avatar from {@code displayName} for now.
 *
 * <p>No float or IQ balance here: there is no wallet/ledger yet, so nothing honest to show.
 */
public record BrandCardResponse(UUID id,
                                String code,
                                String displayName,
                                String logoUrl,
                                OrgStatus status,
                                KycStatus kycStatus,
                                long distributorCount,
                                long retailerCount) {
}
