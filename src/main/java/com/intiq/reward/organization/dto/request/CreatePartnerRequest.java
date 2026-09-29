package com.intiq.reward.organization.dto.request;

import com.intiq.reward.common.constant.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A brand creating its own distributor. Writes the organisation, its login and the channel
 * relationship together. A brand cannot create a retailer directly — see {@code CreateRetailerRequest}
 * for that, a distributor's own action.
 */
public record CreatePartnerRequest(@NotBlank @Size(max = 200) String legalName,
                                   @NotBlank @Size(max = 150) String displayName,
                                   @Pattern(regexp = ValidationPatterns.GSTIN) String gstin,
                                   @Pattern(regexp = ValidationPatterns.PAN) String pan,
                                   @Size(max = 120) String ownerName,
                                   @Pattern(regexp = ValidationPatterns.PHONE) String ownerPhone,
                                   @Email @Size(max = 160) String ownerEmail,
                                   @Size(max = 50) String region) {
}
