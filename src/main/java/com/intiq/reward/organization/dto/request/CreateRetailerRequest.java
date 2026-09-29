package com.intiq.reward.organization.dto.request;

import com.intiq.reward.common.constant.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * A distributor creating a retailer under itself. The retailer starts {@code ACTIVE} immediately —
 * unlike a brand creating a distributor, this needs no admin approval. The creating distributor is
 * taken from the caller's own login, not from the request; {@code brandOrgId} says which brand this
 * retailer serves, since one distributor can supply more than one brand.
 */
public record CreateRetailerRequest(@NotNull UUID brandOrgId,
                                    @NotBlank @Size(max = 200) String legalName,
                                    @NotBlank @Size(max = 150) String displayName,
                                    @Pattern(regexp = ValidationPatterns.GSTIN) String gstin,
                                    @Pattern(regexp = ValidationPatterns.PAN) String pan,
                                    @Size(max = 120) String ownerName,
                                    @Pattern(regexp = ValidationPatterns.PHONE) String ownerPhone,
                                    @Email @Size(max = 160) String ownerEmail,
                                    @Size(max = 50) String region) {
}
