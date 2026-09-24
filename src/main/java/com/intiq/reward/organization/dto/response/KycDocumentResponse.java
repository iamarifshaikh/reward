package com.intiq.reward.organization.dto.response;

import com.intiq.reward.organization.enums.KycDocStatus;
import com.intiq.reward.organization.enums.KycDocType;

import java.time.Instant;
import java.util.UUID;

/**
 * downloadUrl is a short-lived presigned link generated per request, never a permanent location,
 * and only after the caller has passed the access check.
 */
public record KycDocumentResponse(UUID id,
                                  KycDocType docType,
                                  String contentType,
                                  long sizeBytes,
                                  KycDocStatus status,
                                  String remarks,
                                  String downloadUrl,
                                  Instant reviewedAt,
                                  Instant createdAt) {
}
