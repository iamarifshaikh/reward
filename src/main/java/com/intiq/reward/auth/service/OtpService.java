package com.intiq.reward.auth.service;

import com.intiq.reward.auth.config.OtpProperties;
import com.intiq.reward.auth.entity.OtpChallenge;
import com.intiq.reward.auth.enums.OtpChannel;
import com.intiq.reward.auth.enums.OtpPurpose;
import com.intiq.reward.auth.repository.OtpChallengeRepository;
import com.intiq.reward.common.exception.DomainException;
import com.intiq.reward.common.exception.ErrorCode;
import com.intiq.reward.common.util.Hashes;
import com.intiq.reward.common.util.Masking;
import com.intiq.reward.messaging.provider.EmailRequest;
import com.intiq.reward.messaging.provider.EmailSender;
import com.intiq.reward.messaging.provider.SmsRequest;
import com.intiq.reward.messaging.provider.SmsSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * Owns everything about one-time codes: generation, storage, rate limiting, delivery and
 * verification. Kept apart from {@link AuthService} because these rules are security critical,
 * self-contained and heavily tested.
 *
 * <p>The code itself is never stored. Only an HMAC of it is, keyed with a server-side pepper.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private static final String SMS_TEMPLATE_LOGIN = "OTP_LOGIN";

    private final OtpChallengeRepository otpChallengeRepository;
    private final OtpProperties properties;
    private final SmsSender smsSender;
    private final EmailSender emailSender;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /**
     * Creates and sends a code. The caller must not reveal whether the destination is registered,
     * so this returns the same result either way.
     */
    @Transactional
    public OtpIssueResult issue(OtpChannel channel, String destination, OtpPurpose purpose, String requestIp) {
        Instant now = clock.instant();
        enforceRateLimits(destination, requestIp, now);

        String code = generateCode();
        OtpChallenge challenge = OtpChallenge.issue(
                channel, destination, purpose, hash(destination, purpose, code), now, properties.ttl(), requestIp);
        otpChallengeRepository.save(challenge);

        deliver(channel, destination, code);
        log.info("OTP issued channel={} destination={}", channel, mask(channel, destination));

        return new OtpIssueResult((int) properties.ttl().toSeconds(),
                (int) properties.resendCooldown().toSeconds());
    }

    /**
     * Checks a submitted code against the newest challenge for that destination.
     * A wrong code counts an attempt; a correct one consumes the challenge so it cannot be replayed.
     */
    @Transactional
    public void verify(OtpChannel channel, String destination, OtpPurpose purpose, String code) {
        Instant now = clock.instant();
        OtpChallenge challenge = otpChallengeRepository
                .findFirstByDestinationAndPurposeOrderByCreatedAtDesc(destination, purpose)
                .orElseThrow(() -> new DomainException(ErrorCode.AUTH_OTP_NOT_FOUND));

        if (challenge.isConsumed() || challenge.isExpired(now)) {
            throw new DomainException(ErrorCode.AUTH_OTP_EXPIRED);
        }
        if (challenge.getAttempts() >= properties.maxAttempts()) {
            throw new DomainException(ErrorCode.AUTH_OTP_ATTEMPTS_EXCEEDED);
        }
        if (challenge.getChannel() != channel) {
            throw new DomainException(ErrorCode.AUTH_OTP_INVALID);
        }

        if (!Hashes.constantTimeEquals(challenge.getCodeHash(), hash(destination, purpose, code))) {
            challenge.registerFailedAttempt();
            throw new DomainException(ErrorCode.AUTH_OTP_INVALID);
        }

        challenge.consume(now);
    }

    /**
     * Two windows, because they stop different things: per destination stops pestering one person,
     * per IP stops one machine harvesting many numbers.
     */
    private void enforceRateLimits(String destination, String requestIp, Instant now) {
        long recentForDestination = otpChallengeRepository
                .countByDestinationAndCreatedAtAfter(destination, now.minus(properties.rateWindow()));
        if (recentForDestination >= properties.maxPerDestination()) {
            throw new DomainException(ErrorCode.COMMON_RATE_LIMITED);
        }

        long sinceCooldown = otpChallengeRepository
                .countByDestinationAndCreatedAtAfter(destination, now.minus(properties.resendCooldown()));
        if (sinceCooldown > 0) {
            throw new DomainException(ErrorCode.AUTH_OTP_COOLDOWN);
        }

        if (requestIp != null) {
            long recentForIp = otpChallengeRepository
                    .countByRequestIpAndCreatedAtAfter(requestIp, now.minus(properties.ipRateWindow()));
            if (recentForIp >= properties.maxPerIp()) {
                throw new DomainException(ErrorCode.COMMON_RATE_LIMITED);
            }
        }
    }

    private void deliver(OtpChannel channel, String destination, String code) {
        String text = "Your INTIQ Rewards code is " + code + ". It is valid for "
                + properties.ttl().toMinutes() + " minutes. Do not share it with anyone.";

        if (channel == OtpChannel.SMS) {
            smsSender.send(SmsRequest.of(destination, SMS_TEMPLATE_LOGIN, Map.of("otp", code), text));
        } else {
            emailSender.send(EmailRequest.of(destination, "Your INTIQ Rewards login code", text));
        }
    }

    /**
     * Destination and purpose are both part of the hash, so a code issued for one contact, or for
     * confirming a contact change, cannot be replayed against a different contact or on the login screen.
     */
    private String hash(String destination, OtpPurpose purpose, String code) {
        return Hashes.hmacSha256Hex(properties.pepper(), destination + ":" + purpose.name() + ":" + code);
    }

    private String generateCode() {
        int bound = (int) Math.pow(10, properties.codeLength());
        return String.format("%0" + properties.codeLength() + "d", random.nextInt(bound));
    }

    private String mask(OtpChannel channel, String destination) {
        return channel == OtpChannel.SMS ? Masking.phone(destination) : Masking.email(destination);
    }

    /** What the client needs to render the "resend in 30s" countdown, and nothing more. */
    public record OtpIssueResult(int expiresInSeconds, int retryAfterSeconds) {
    }
}
