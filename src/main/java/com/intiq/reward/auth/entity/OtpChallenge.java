package com.intiq.reward.auth.entity;

import com.intiq.reward.auth.enums.OtpChannel;
import com.intiq.reward.auth.enums.OtpPurpose;
import com.intiq.reward.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/**
 * One OTP in flight. Keyed by destination rather than by user, so a code can be sent before we
 * know who owns the contact, and a flood of failed attempts never touches the user row.
 * Only the hash of the code is stored.
 */
@Entity
@Table(name = "otp_challenges")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OtpChallenge extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 8)
    private OtpChannel channel;

    /** Phone in E.164, or an email address. */
    @Column(name = "destination", nullable = false, length = 150)
    private String destination;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 16)
    private OtpPurpose purpose;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "attempts", nullable = false)
    private short attempts;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "request_ip", length = 45)
    private String requestIp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static OtpChallenge issue(OtpChannel channel,
                                     String destination,
                                     OtpPurpose purpose,
                                     String codeHash,
                                     Instant now,
                                     Duration validity,
                                     String requestIp) {
        OtpChallenge challenge = new OtpChallenge();
        challenge.channel = channel;
        challenge.destination = destination;
        challenge.purpose = purpose;
        challenge.codeHash = codeHash;
        challenge.attempts = 0;
        challenge.createdAt = now;
        challenge.expiresAt = now.plus(validity);
        challenge.requestIp = requestIp;
        return challenge;
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isUsable(Instant now, int maxAttempts) {
        return !isConsumed() && !isExpired(now) && attempts < maxAttempts;
    }

    public void registerFailedAttempt() {
        attempts++;
    }

    public void consume(Instant now) {
        consumedAt = now;
    }
}
