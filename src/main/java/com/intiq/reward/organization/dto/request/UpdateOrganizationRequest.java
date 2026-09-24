package com.intiq.reward.organization.dto.request;

import com.intiq.reward.common.constant.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateOrganizationRequest(@NotBlank @Size(max = 200) String legalName,
                                        @NotBlank @Size(max = 150) String displayName,
                                        @Pattern(regexp = ValidationPatterns.GSTIN) String gstin,
                                        @Pattern(regexp = ValidationPatterns.PAN) String pan) {
}
