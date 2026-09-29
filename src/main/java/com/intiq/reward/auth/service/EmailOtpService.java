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
import com.intiq.reward.messaging.Channel;
import com.intiq.reward.messaging.MessageDispatcher;
import com.intiq.reward.messaging.MessageRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * Email OTP, generated, stored and verified entirely by us — unlike {@link SmsOtpService}, where
 * MSG91 owns the whole lifecycle. AWS SES only delivers the text; nothing about the code's state
 * lives outside this service and {@code otp_challenges}.
 *
 * <p>The code itself is never stored. Only an HMAC of it is, keyed with a server-side pepper.
 *
 * <p>{@code otp_challenges.channel} is now always {@code EMAIL} — it is kept in the schema rather
 * than dropped, since removing it buys nothing and a column is cheap to leave in place.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailOtpService {

    private static final String TEMPLATE_LOGIN = "OTP_LOGIN";

    private final OtpChallengeRepository otpChallengeRepository;
    private final OtpProperties properties;
    private final MessageDispatcher messageDispatcher;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /**
     * Creates and sends a code. The caller must not reveal whether the destination is registered,
     * so this returns the same result either way.
     */
    @Transactional
    public OtpIssueResult issue(String destination, OtpPurpose purpose, String requestIp) {
        Instant now = clock.instant();
        enforceRateLimits(destination, requestIp, now);

        String code = generateCode();
        OtpChallenge challenge = OtpChallenge.issue(
                OtpChannel.EMAIL, destination, purpose, hash(destination, purpose, code), now, properties.ttl(), requestIp);
        otpChallengeRepository.save(challenge);

        deliver(destination, code);
        log.info("Email OTP issued destination={}", mask(destination));

        return new OtpIssueResult((int) properties.ttl().toSeconds(),
                (int) properties.resendCooldown().toSeconds());
    }

    /**
     * Checks a submitted code against the newest challenge for that destination.
     * A wrong code counts an attempt; a correct one consumes the challenge so it cannot be replayed.
     */
    @Transactional
    public void verify(String destination, OtpPurpose purpose, String code) {
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

    private void deliver(String destination, String code) {
        String text = "Your INTIQ Rewards code is " + code + ". It is valid for "
                + properties.ttl().toMinutes() + " minutes. Do not share it with anyone.";

        messageDispatcher.send(MessageRequest.of(
                Channel.EMAIL,
                destination,
                null,
                TEMPLATE_LOGIN,
                Map.of("OTP", code),
                "Your INTIQ Rewards login code",
                text));
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

    private String mask(String destination) {
        return Masking.email(destination);
    }
}
