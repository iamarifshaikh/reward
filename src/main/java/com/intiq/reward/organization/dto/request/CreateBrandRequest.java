package com.intiq.reward.organization.dto.request;

import com.intiq.reward.common.constant.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Platform admin creating a brand. Creates the organisation and its single login in one call,
 * which is why the owner contact is part of this request.
 */
public record CreateBrandRequest(@NotBlank @Size(max = 200) String legalName,
                                 @NotBlank @Size(max = 150) String displayName,
                                 @Pattern(regexp = ValidationPatterns.GSTIN) String gstin,
                                 @Pattern(regexp = ValidationPatterns.PAN) String pan,
                                 @Size(max = 120) String ownerName,
                                 @Pattern(regexp = ValidationPatterns.PHONE) String ownerPhone,
                                 @Email @Size(max = 160) String ownerEmail) {
}
