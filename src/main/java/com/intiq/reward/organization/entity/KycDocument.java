package com.intiq.reward.organization.entity;

import com.intiq.reward.common.entity.BaseEntity;
import com.intiq.reward.organization.enums.KycDocStatus;
import com.intiq.reward.organization.enums.KycDocType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One uploaded KYC proof. The file itself lives in a private S3 bucket; this row holds only the
 * object key, so the document is reached through a short-lived presigned URL after an access check.
 */
@Entity
@Table(name = "kyc_documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KycDocument extends BaseEntity {

    @Column(name = "org_id", nullable = false, updatable = false)
    private UUID orgId;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 24, updatable = false)
    private KycDocType docType;

    @Column(name = "object_key", nullable = false, length = 512, updatable = false)
    private String objectKey;

    @Column(name = "content_type", nullable = false, length = 100, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private KycDocStatus status;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    public static KycDocument upload(UUID orgId,
                                     KycDocType docType,
                                     String objectKey,
                                     String contentType,
                                     long sizeBytes,
                                     UUID uploadedBy,
                                     Instant now) {
        KycDocument document = new KycDocument();
        document.orgId = orgId;
        document.docType = docType;
        document.objectKey = objectKey;
        document.contentType = contentType;
        document.sizeBytes = sizeBytes;
        document.status = KycDocStatus.PENDING;
        document.createdBy = uploadedBy;
        document.createdAt = now;
        return document;
    }

    public void approve(UUID reviewerUserId, Instant now) {
        requirePending();
        this.status = KycDocStatus.APPROVED;
        this.reviewedBy = reviewerUserId;
        this.reviewedAt = now;
    }

    public void reject(UUID reviewerUserId, String remarks, Instant now) {
        requirePending();
        this.status = KycDocStatus.REJECTED;
        this.reviewedBy = reviewerUserId;
        this.reviewedAt = now;
        this.remarks = remarks;
    }

    /** Marked when the business uploads a newer file of the same type. */
    public void supersede() {
        this.status = KycDocStatus.SUPERSEDED;
    }

    public boolean isPending() {
        return status == KycDocStatus.PENDING;
    }

    private void requirePending() {
        if (status != KycDocStatus.PENDING) {
            throw new IllegalStateException("Only a pending document can be reviewed");
        }
    }
}
