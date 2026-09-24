package com.intiq.reward.auth.security;

import com.intiq.reward.common.enums.ActorType;
import com.intiq.reward.common.security.AuthPrincipal;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Turns a verified token into the principal the application works with.
 *
 * <p>The actor type becomes an authority (ROLE_ADMIN, ROLE_BRAND, …) so route-level rules can use
 * {@code hasRole}. The organisation id stays on the principal, where {@code @orgAccess} checks it
 * against the path. Both halves are needed: type alone would let one brand act on another.
 */
@Component
public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        ActorType actorType = ActorType.valueOf(jwt.getClaimAsString(JwtIssuer.CLAIM_ACTOR_TYPE));
        String orgClaim = jwt.getClaimAsString(JwtIssuer.CLAIM_ORG_ID);

        AuthPrincipal principal = new AuthPrincipal(
                UUID.fromString(jwt.getSubject()),
                orgClaim == null ? null : UUID.fromString(orgClaim),
                actorType);

        return new AuthPrincipalAuthenticationToken(principal, jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + actorType.name())));
    }
}
