package com.intiq.reward.auth.security;

import com.intiq.reward.common.security.AuthPrincipal;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;

/**
 * Carries our own {@link AuthPrincipal} as the principal instead of the raw JWT, so services and
 * controllers never parse claims themselves.
 */
public class AuthPrincipalAuthenticationToken extends AbstractAuthenticationToken {

    private final transient AuthPrincipal principal;
    private final transient Jwt token;

    public AuthPrincipalAuthenticationToken(AuthPrincipal principal,
                                            Jwt token,
                                            Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.token = token;
        setAuthenticated(true);
    }

    @Override
    public AuthPrincipal getPrincipal() {
        return principal;
    }

    @Override
    public Jwt getCredentials() {
        return token;
    }
}
