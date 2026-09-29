package com.intiq.reward.organization.entity;

import com.intiq.reward.common.entity.AuditableEntity;
import com.intiq.reward.organization.enums.KycStatus;
import com.intiq.reward.organization.enums.OrgStatus;
import com.intiq.reward.organization.enums.OrgType;
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
 * A business on the platform: the platform itself, a brand, a distributor or a retailer.
 * {@code orgType} is what decides the permissions of the login attached to it.
 * PAN is held encrypted, with a hash alongside it for duplicate detection.
 */
@Entity
@Table(name = "organizations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Organization extends AuditableEntity {

    /** Human-readable identifier such as BRD-00042, used in support and exports. */
    @Column(name = "code", nullable = false, length = 20, updatable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "org_type", nullable = false, length = 16, updatable = false)
    private OrgType orgType;

    @Column(name = "legal_name", nullable = false, length = 200)
    private String legalName;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(name = "gstin", length = 15)
    private String gstin;

    @Column(name = "pan_enc")
    private byte[] panEnc;

    @Column(name = "pan_hash", length = 64)
    private String panHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrgStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 16)
    private KycStatus kycStatus;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    /** Brand only. */
    @Column(name = "logo_object_key", length = 512)
    private String logoObjectKey;

    public static Organization create(OrgType orgType,
                                      String code,
                                      String legalName,
                                      String displayName,
                                      OrgStatus initialStatus) {
        Organization org = new Organization();
        org.orgType = orgType;
        org.code = code;
        org.legalName = legalName;
        org.displayName = displayName;
        org.status = initialStatus;
        org.kycStatus = KycStatus.NOT_STARTED;
        return org;
    }

    public void updateProfile(String legalName, String displayName, String gstin) {
        this.legalName = legalName;
        this.displayName = displayName;
        this.gstin = gstin;
    }

    /** Encryption happens in the service; the entity only ever sees the ciphertext and its hash. */
    public void updatePan(byte[] panEnc, String panHash) {
        this.panEnc = panEnc;
        this.panHash = panHash;
    }

    public void updateLogo(String objectKey) {
        this.logoObjectKey = objectKey;
    }

    /** Status, timestamp and approver move together, so they can never disagree. */
    public void approve(UUID adminUserId, Instant now) {
        if (status == OrgStatus.CLOSED) {
            throw new IllegalStateException("A closed organization cannot be approved");
        }
        this.status = OrgStatus.ACTIVE;
        this.approvedBy = adminUserId;
        this.approvedAt = now;
    }

    /** A business that never made it past review. Distinct from {@link #close}, which ends one that did. */
    public void reject() {
        if (status != OrgStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Only a pending organization can be rejected");
        }
        this.status = OrgStatus.REJECTED;
    }

    /** A rejected organization can be corrected and put back in front of an admin. */
    public void resubmit() {
        if (status != OrgStatus.REJECTED) {
            throw new IllegalStateException("Only a rejected organization can be resubmitted");
        }
        this.status = OrgStatus.PENDING_APPROVAL;
    }

    public void suspend() {
        if (status != OrgStatus.ACTIVE) {
            throw new IllegalStateException("Only an active organization can be suspended");
        }
        this.status = OrgStatus.SUSPENDED;
    }

    public void reactivate() {
        if (status != OrgStatus.SUSPENDED) {
            throw new IllegalStateException("Only a suspended organization can be reactivated");
        }
        this.status = OrgStatus.ACTIVE;
    }

    public void close() {
        this.status = OrgStatus.CLOSED;
    }

    public void markKycSubmitted() {
        this.kycStatus = KycStatus.SUBMITTED;
    }

    public void markKycVerified() {
        if (kycStatus != KycStatus.SUBMITTED) {
            throw new IllegalStateException("KYC must be submitted before it can be verified");
        }
        this.kycStatus = KycStatus.VERIFIED;
    }

    public void markKycRejected() {
        if (kycStatus != KycStatus.SUBMITTED) {
            throw new IllegalStateException("KYC must be submitted before it can be rejected");
        }
        this.kycStatus = KycStatus.REJECTED;
    }

    public boolean isActive() {
        return status == OrgStatus.ACTIVE;
    }

    public boolean isBrand() {
        return orgType == OrgType.BRAND;
    }

    public boolean isPartner() {
        return orgType == OrgType.DISTRIBUTOR || orgType == OrgType.RETAILER;
    }
}
