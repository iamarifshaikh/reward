package com.intiq.reward.organization.dto.request;

import com.intiq.reward.common.constant.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A brand correcting a distributor it created, while the organisation is still
 * {@code PENDING_APPROVAL} or {@code REJECTED}. Once approved, this endpoint refuses — see
 * {@code OrganizationService#updatePartner}.
 */
public record UpdatePendingPartnerRequest(@NotBlank @Size(max = 200) String legalName,
                                          @NotBlank @Size(max = 150) String displayName,
                                          @Pattern(regexp = ValidationPatterns.GSTIN) String gstin,
                                          @Pattern(regexp = ValidationPatterns.PAN) String pan,
                                          @Size(max = 120) String ownerName,
                                          @Pattern(regexp = ValidationPatterns.PHONE) String ownerPhone,
                                          @Email @Size(max = 160) String ownerEmail,
                                          @Size(max = 50) String region) {
}
