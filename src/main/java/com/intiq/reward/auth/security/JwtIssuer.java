package com.intiq.reward.auth.security;

import com.intiq.reward.auth.config.JwtProperties;
import com.intiq.reward.auth.enums.ContextType;
import com.intiq.reward.common.enums.ActorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Builds the access token. Claims are kept to the minimum needed to authorise a request without a
 * database read: who, for which organisation, acting as what.
 *
 * <p>Permissions are deliberately not in the token. They follow from {@code actorType}, so a
 * change to what a retailer may do takes effect immediately rather than when tokens expire.
 */
@Component
@RequiredArgsConstructor
public class JwtIssuer {

    public static final String CLAIM_ORG_ID = "org";
    public static final String CLAIM_ACTOR_TYPE = "actor";
    public static final String CLAIM_CONTEXT = "ctx";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final Clock clock;

    public AccessToken issue(UUID userId, UUID orgId, ActorType actorType, ContextType contextType) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.accessTtl());

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_ACTOR_TYPE, actorType.name())
                .claim(CLAIM_CONTEXT, contextType.name());

        if (orgId != null) {
            claims.claim(CLAIM_ORG_ID, orgId.toString());
        }

        String value = jwtEncoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256).build(),
                        claims.build())).getTokenValue();

        return new AccessToken(value, properties.accessTtl().toSeconds());
    }

    public record AccessToken(String value, long expiresInSeconds) {
    }
}
