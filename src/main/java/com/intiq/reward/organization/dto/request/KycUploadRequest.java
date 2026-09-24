package com.intiq.reward.organization.dto.request;

import com.intiq.reward.organization.enums.KycDocType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Registers a file the client has already uploaded to its presigned URL. The service re-checks the
 * stored object rather than trusting the size and content type sent here.
 */
public record KycUploadRequest(@NotNull KycDocType docType,
                               @NotBlank @Size(max = 512) String objectKey,
                               @NotBlank @Size(max = 100) String contentType,
                               @Positive @Max(10485760) long sizeBytes) {
}
