package com.intiq.reward.organization.dto.request;

import com.intiq.reward.common.constant.ValidationPatterns;
import com.intiq.reward.organization.enums.PartnerType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * A brand creating a distributor or retailer. Writes the organisation, its login and the channel
 * relationship together. parentOrgId is the supplying distributor, and applies to retailers only.
 */
public record CreatePartnerRequest(@NotNull PartnerType partnerType,
                                   @NotBlank @Size(max = 200) String legalName,
                                   @NotBlank @Size(max = 150) String displayName,
                                   @Pattern(regexp = ValidationPatterns.GSTIN) String gstin,
                                   @Pattern(regexp = ValidationPatterns.PAN) String pan,
                                   @Size(max = 120) String ownerName,
                                   @Pattern(regexp = ValidationPatterns.PHONE) String ownerPhone,
                                   @Email @Size(max = 160) String ownerEmail,
                                   UUID parentOrgId,
                                   @Size(max = 50) String region) {
}
