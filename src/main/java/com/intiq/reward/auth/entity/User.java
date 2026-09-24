package com.intiq.reward.auth.entity;

import com.intiq.reward.auth.enums.Gender;
import com.intiq.reward.auth.enums.UserStatus;
import com.intiq.reward.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Every login on the platform: one per business, plus every consumer.
 * {@code orgId} set means this login acts for that business; null means a consumer.
 * The same row can be both when a business owner is also enrolled as a consumer.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends AuditableEntity {

    @Column(name = "phone", length = 15)
    private String phone;

    @Column(name = "email", length = 160)
    private String email;

    @Column(name = "full_name", length = 120)
    private String fullName;

    /** Reference to organizations.id. Deliberately a plain id: organization is a separate module. */
    @Column(name = "org_id")
    private UUID orgId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private UserStatus status;

    @Column(name = "phone_verified_at")
    private Instant phoneVerifiedAt;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "consumer_enrolled_at")
    private Instant consumerEnrolledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 8)
    private Gender gender;

    @Column(name = "city", length = 80)
    private String city;

    @Column(name = "pincode", length = 6)
    private String pincode;

    /** Retailer that enrolled this consumer. */
    @Column(name = "enrolled_by_org_id")
    private UUID enrolledByOrgId;

    /** Login for a brand, distributor, retailer or the platform, created by whoever onboarded them. */
    public static User businessLogin(UUID orgId, String phone, String email, String fullName) {
        User user = new User();
        user.orgId = orgId;
        user.setContact(phone, email);
        user.fullName = fullName;
        user.status = UserStatus.UNVERIFIED;
        return user;
    }

    /**
     * Consumer created by a retailer, or self-enrolled later.
     * A counter enrolment usually has only a phone, so either contact alone is enough.
     */
    public static User consumer(String phone, String email, String fullName, UUID enrolledByOrgId, Instant now) {
        User user = new User();
        user.setContact(phone, email);
        user.fullName = fullName;
        user.enrolledByOrgId = enrolledByOrgId;
        user.consumerEnrolledAt = now;
        user.status = UserStatus.UNVERIFIED;
        return user;
    }

    /**
     * An existing login (often a business owner) now also shops as a consumer.
     * Keeps the first enrolling store, and never touches the business side of the row.
     */
    public void enrollAsConsumer(UUID retailerOrgId, Instant now) {
        if (consumerEnrolledAt == null) {
            consumerEnrolledAt = now;
            enrolledByOrgId = retailerOrgId;
        }
    }

    public void updateConsumerProfile(String fullName, Gender gender, String city, String pincode) {
        this.fullName = fullName;
        this.gender = gender;
        this.city = city;
        this.pincode = pincode;
    }

    /**
     * Correcting a mistyped contact is only safe while nobody has proved ownership of it.
     * Once active, a change must be verified by an OTP to the new contact.
     */
    public void correctContact(String phone, String email) {
        if (status != UserStatus.UNVERIFIED) {
            throw new IllegalStateException("Contact can only be corrected while the login is unverified");
        }
        setContact(phone, email);
    }

    public void verifyPhone(Instant now) {
        phoneVerifiedAt = now;
        activateIfUnverified();
    }

    public void verifyEmail(Instant now) {
        emailVerifiedAt = now;
        activateIfUnverified();
    }

    public void recordLogin(Instant now) {
        lastLoginAt = now;
    }

    public void block() {
        status = UserStatus.BLOCKED;
    }

    public void unblock() {
        if (status == UserStatus.BLOCKED) {
            status = hasVerifiedContact() ? UserStatus.ACTIVE : UserStatus.UNVERIFIED;
        }
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isBusinessLogin() {
        return orgId != null;
    }

    public boolean isConsumer() {
        return consumerEnrolledAt != null;
    }

    /**
     * Mirrors the users_contact check constraint, and keeps email lower-cased so the unique index
     * and every lookup agree on casing.
     */
    private void setContact(String phone, String email) {
        if (!StringUtils.hasText(phone) && !StringUtils.hasText(email)) {
            throw new IllegalArgumentException("A login needs at least a phone or an email");
        }
        this.phone = StringUtils.hasText(phone) ? phone.trim() : null;
        this.email = StringUtils.hasText(email) ? email.trim().toLowerCase(Locale.ROOT) : null;
    }

    private void activateIfUnverified() {
        if (status == UserStatus.UNVERIFIED) {
            status = UserStatus.ACTIVE;
        }
    }

    private boolean hasVerifiedContact() {
        return phoneVerifiedAt != null || emailVerifiedAt != null;
    }
}