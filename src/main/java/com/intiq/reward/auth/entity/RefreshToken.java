package com.intiq.reward.auth.entity;

import com.intiq.reward.auth.enums.ContextType;
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
import java.util.UUID;

/**
 * One issued refresh token. Every rotation of the same login shares a {@code familyId}: if a token
 * that was already spent is presented again, it was copied, and the whole family is revoked.
 * Only the hash of the token is stored.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "context_type", nullable = false, length = 8, updatable = false)
    private ContextType contextType;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** First token of a new login: starts a new family. */
    public static RefreshToken startFamily(UUID userId,
                                           ContextType contextType,
                                           String tokenHash,
                                           Instant now,
                                           Duration validity,
                                           String userAgent,
                                           String ip) {
        return create(userId, UUID.randomUUID(), contextType, tokenHash, now, validity, userAgent, ip);
    }

    /** Replacement token issued on refresh: stays in the same family. */
    public RefreshToken rotate(String newTokenHash, Instant now, Duration validity) {
        return create(userId, familyId, contextType, newTokenHash, now, validity, userAgent, ip);
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    private static RefreshToken create(UUID userId,
                                       UUID familyId,
                                       ContextType contextType,
                                       String tokenHash,
                                       Instant now,
                                       Duration validity,
                                       String userAgent,
                                       String ip) {
        RefreshToken token = new RefreshToken();
        token.userId = userId;
        token.familyId = familyId;
        token.contextType = contextType;
        token.tokenHash = tokenHash;
        token.createdAt = now;
        token.expiresAt = now.plus(validity);
        token.userAgent = userAgent;
        token.ip = ip;
        return token;
    }
}
