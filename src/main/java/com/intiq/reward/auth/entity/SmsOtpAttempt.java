package com.intiq.reward.auth.entity;

import com.intiq.reward.auth.enums.SmsOtpOutcome;
import com.intiq.reward.auth.enums.SmsOtpStage;
import com.intiq.reward.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One MSG91 SendOTP or Verify OTP call, and what MSG91 said back.
 *
 * <p>MSG91 owns the code itself now, so this row holds no code or hash — it exists purely to
 * throttle our own SendOTP calls and to leave a trail of MSG91's raw answers, since that is the
 * only OTP state left on our side for this channel.
 */
@Entity
@Table(name = "sms_otp_attempts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SmsOtpAttempt extends BaseEntity {

    @Column(name = "destination", nullable = false, length = 15, updatable = false)
    private String destination;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 8, updatable = false)
    private SmsOtpStage stage;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 16, updatable = false)
    private SmsOtpOutcome outcome;

    /** MSG91's raw response text, truncated. What you read when a user says "I got nothing". */
    @Column(name = "provider_response", length = 500)
    private String providerResponse;

    @Column(name = "request_ip", length = 45, updatable = false)
    private String requestIp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static SmsOtpAttempt record(String destination,
                                       SmsOtpStage stage,
                                       SmsOtpOutcome outcome,
                                       String providerResponse,
                                       String requestIp,
                                       Instant now) {
        SmsOtpAttempt attempt = new SmsOtpAttempt();
        attempt.destination = destination;
        attempt.stage = stage;
        attempt.outcome = outcome;
        attempt.providerResponse = truncate(providerResponse);
        attempt.requestIp = requestIp;
        attempt.createdAt = now;
        return attempt;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
