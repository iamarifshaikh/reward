package com.intiq.reward.auth.service;

import com.intiq.reward.audit.constant.AuditActions;
import com.intiq.reward.audit.service.AuditService;
import com.intiq.reward.auth.config.OtpProperties;
import com.intiq.reward.auth.entity.SmsOtpAttempt;
import com.intiq.reward.auth.enums.SmsOtpOutcome;
import com.intiq.reward.auth.enums.SmsOtpStage;
import com.intiq.reward.auth.provider.Msg91OtpClient;
import com.intiq.reward.auth.provider.Msg91OtpClient.Msg91OtpResponse;
import com.intiq.reward.auth.repository.SmsOtpAttemptRepository;
import com.intiq.reward.common.exception.DomainException;
import com.intiq.reward.common.exception.ErrorCode;
import com.intiq.reward.common.util.Masking;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * SMS OTP, fully delegated to MSG91's OTP product: MSG91 generates the code, holds it, and answers
 * whether a guess was correct. We hold none of that state — only a rate-limit and diagnostic trail
 * in {@code sms_otp_attempts}.
 *
 * <p>Deliberately separate from {@link EmailOtpService}. That service owns a code end to end and
 * needs a table describing the code's own state; this one owns nothing but "did I recently ask,
 * and what did MSG91 last say", which is a different shape of problem.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmsOtpService {

    private final Msg91OtpClient msg91OtpClient;
    private final SmsOtpAttemptRepository attemptRepository;
    private final OtpProperties properties;
    private final AuditService auditService;
    private final Clock clock;

    /**
     * The MSG91-side expiry is whatever the template is configured with in their panel (their
     * default is 15 minutes) — we do not control or know it precisely here, so this is a display
     * hint for the client's countdown, not an enforced value.
     */
    private static final int ASSUMED_EXPIRY_SECONDS = 15 * 60;

    /**
     * Rate limiting happens here, before MSG91 is ever called — {@link AuthService} has already
     * confirmed the destination belongs to a real, unblocked user by this point, so there is no
     * account-enumeration concern left to protect against; this purely stops one number or one IP
     * from burning MSG91 send credits with no throttle on our side.
     */
    @Transactional
    public OtpIssueResult issue(String destination, String requestIp) {
        Instant now = clock.instant();
        enforceRateLimits(destination, requestIp, now);

        Msg91OtpResponse response;
        SmsOtpOutcome outcome;
        try {
            response = msg91OtpClient.send(destination);
            outcome = response.success() ? SmsOtpOutcome.ACCEPTED : SmsOtpOutcome.REJECTED;
        } catch (Msg91OtpClient.Msg91OtpException e) {
            recordAttempt(destination, SmsOtpStage.SEND, SmsOtpOutcome.ERROR, e.getMessage(), requestIp, now);
            log.error("MSG91 SendOTP failed destination={}", mask(destination), e);
            throw new DomainException(ErrorCode.AUTH_OTP_PROVIDER_UNAVAILABLE);
        }

        recordAttempt(destination, SmsOtpStage.SEND, outcome, response.message(), requestIp, now);

        if (!response.success()) {
            log.warn("MSG91 rejected SendOTP destination={} message={}", mask(destination), response.message());
            throw new DomainException(ErrorCode.AUTH_OTP_PROVIDER_REJECTED, response.message());
        }

        log.info("SMS OTP requested via MSG91 destination={}", mask(destination));
        return new OtpIssueResult(ASSUMED_EXPIRY_SECONDS, (int) properties.resendCooldown().toSeconds());
    }

    /**
     * Asks MSG91 whether the code was correct. MSG91's own wording decides which error the caller
     * sees; anything we do not recognise is treated as an invalid code rather than silently passing.
     */
    @Transactional
    public void verify(String destination, String code) {
        Instant now = clock.instant();
        Msg91OtpResponse response;
        try {
            response = msg91OtpClient.verify(destination, code);
        } catch (Msg91OtpClient.Msg91OtpException e) {
            recordAttempt(destination, SmsOtpStage.VERIFY, SmsOtpOutcome.ERROR, e.getMessage(), null, now);
            log.error("MSG91 Verify OTP failed destination={}", mask(destination), e);
            throw new DomainException(ErrorCode.AUTH_OTP_PROVIDER_UNAVAILABLE);
        }

        if (response.success()) {
            recordAttempt(destination, SmsOtpStage.VERIFY, SmsOtpOutcome.VERIFIED, response.message(), null, now);
            return;
        }

        String message = response.message() == null ? "" : response.message().toLowerCase();
        if (message.contains("expired")) {
            recordAttempt(destination, SmsOtpStage.VERIFY, SmsOtpOutcome.EXPIRED, response.message(), null, now);
            throw new DomainException(ErrorCode.AUTH_OTP_EXPIRED);
        }

        // Covers "Invalid OTP" and anything unrecognised — never treat an unclear answer as success.
        recordAttempt(destination, SmsOtpStage.VERIFY, SmsOtpOutcome.INVALID, response.message(), null, now);
        throw new DomainException(ErrorCode.AUTH_OTP_INVALID);
    }

    /**
     * Two windows, matching {@link EmailOtpService}'s own limits, reusing the same
     * {@link OtpProperties} values so both channels behave the same from a user's point of view
     * even though the storage underneath is completely different.
     */
    private void enforceRateLimits(String destination, String requestIp, Instant now) {
        long recentForDestination = attemptRepository.countByDestinationAndStageAndCreatedAtAfter(
                destination, SmsOtpStage.SEND, now.minus(properties.rateWindow()));
        if (recentForDestination >= properties.maxPerDestination()) {
            throw new DomainException(ErrorCode.COMMON_RATE_LIMITED);
        }

        long sinceCooldown = attemptRepository.countByDestinationAndStageAndCreatedAtAfter(
                destination, SmsOtpStage.SEND, now.minus(properties.resendCooldown()));
        if (sinceCooldown > 0) {
            throw new DomainException(ErrorCode.AUTH_OTP_COOLDOWN);
        }

        if (requestIp != null) {
            long recentForIp = attemptRepository.countByRequestIpAndStageAndCreatedAtAfter(
                    requestIp, SmsOtpStage.SEND, now.minus(properties.ipRateWindow()));
            if (recentForIp >= properties.maxPerIp()) {
                throw new DomainException(ErrorCode.COMMON_RATE_LIMITED);
            }
        }
    }

    /** Writes the raw-response diagnostic row, and a summary into the shared admin audit trail. */
    private void recordAttempt(String destination,
                               SmsOtpStage stage,
                               SmsOtpOutcome outcome,
                               String providerResponse,
                               String requestIp,
                               Instant now) {
        attemptRepository.save(
                SmsOtpAttempt.record(destination, stage, outcome, providerResponse, requestIp, now));

        auditService.recordAnonymous(
                stage == SmsOtpStage.SEND ? AuditActions.SMS_OTP_PROVIDER_SEND : AuditActions.SMS_OTP_PROVIDER_VERIFY,
                AuditActions.ENTITY_USER,
                null,
                Map.of("destination", mask(destination), "outcome", outcome.name(),
                        "providerMessage", String.valueOf(providerResponse)),
                requestIp);
    }

    private String mask(String destination) {
        return Masking.phone(destination);
    }
}
