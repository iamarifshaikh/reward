package com.intiq.reward.auth.service;

import com.intiq.reward.audit.constant.AuditActions;
import com.intiq.reward.audit.service.AuditService;
import com.intiq.reward.auth.config.JwtProperties;
import com.intiq.reward.auth.entity.RefreshToken;
import com.intiq.reward.auth.enums.ContextType;
import com.intiq.reward.auth.repository.RefreshTokenRepository;
import com.intiq.reward.common.exception.DomainException;
import com.intiq.reward.common.exception.ErrorCode;
import com.intiq.reward.common.util.Hashes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Owns refresh tokens: the part of a session the server can actually revoke.
 *
 * <p>Access tokens are stateless and cannot be withdrawn once issued, which is why they live only
 * 15 minutes. Refresh tokens are stored as a hash, single use, and rotated: presenting a token that
 * was already spent means someone copied it, so the whole login chain is killed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties properties;
    private final AuditService auditService;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /** Starts a new login chain after a successful OTP. */
    @Transactional
    public IssuedToken startSession(UUID userId, ContextType contextType, String userAgent, String ip) {
        Instant now = clock.instant();
        String raw = newToken();

        RefreshToken token = RefreshToken.startFamily(
                userId, contextType, Hashes.sha256Hex(raw), now, properties.refreshTtl(), userAgent, ip);
        refreshTokenRepository.save(token);

        return new IssuedToken(raw, token.getExpiresAt(), userId, contextType);
    }

    /**
     * Exchanges a refresh token for the next one in the same family.
     * The old token is revoked in the same transaction, so it can only ever be used once.
     */
    @Transactional
    public IssuedToken rotate(String rawToken, String userAgent, String ip) {
        Instant now = clock.instant();
        RefreshToken stored = refreshTokenRepository.findByTokenHash(Hashes.sha256Hex(rawToken))
                .orElseThrow(() -> new DomainException(ErrorCode.AUTH_TOKEN_INVALID));

        if (stored.getRevokedAt() != null) {
            handleReuse(stored, now);
        }
        if (!stored.isActive(now)) {
            throw new DomainException(ErrorCode.AUTH_TOKEN_INVALID);
        }

        stored.revoke(now);
        String raw = newToken();
        RefreshToken next = stored.rotate(Hashes.sha256Hex(raw), now, properties.refreshTtl());
        refreshTokenRepository.save(next);

        return new IssuedToken(raw, next.getExpiresAt(), stored.getUserId(), stored.getContextType());
    }

    /** Logout: ends this one session, leaving other devices signed in. */
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(Hashes.sha256Hex(rawToken))
                .ifPresent(token -> token.revoke(clock.instant()));
    }

    /** Used on block, suspension and contact change: every device is signed out. */
    @Transactional
    public int revokeAllForUser(UUID userId) {
        return refreshTokenRepository.revokeAllForUser(userId, clock.instant());
    }

    /**
     * A spent token was presented again. Either the real user replayed an old one, or someone
     * stole it; we cannot tell which, so the safe move is to end the whole chain and make
     * everyone sign in again.
     */
    private void handleReuse(RefreshToken stored, Instant now) {
        refreshTokenRepository.revokeFamily(stored.getFamilyId(), now);
        log.warn("Refresh token reuse detected for user={} family={}", stored.getUserId(), stored.getFamilyId());
        auditService.recordAnonymous(
                AuditActions.TOKEN_REUSE_DETECTED,
                AuditActions.ENTITY_USER,
                stored.getUserId(),
                Map.of("familyId", stored.getFamilyId().toString()),
                stored.getIp());
        throw new DomainException(ErrorCode.AUTH_TOKEN_REUSED);
    }

    /** 256 bits of randomness: the token is the credential, so it needs no structure. */
    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * @param rawToken the value handed to the client; only its hash is stored
     */
    public record IssuedToken(String rawToken, Instant expiresAt, UUID userId, ContextType contextType) {
    }
}
