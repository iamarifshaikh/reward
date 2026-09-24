package com.intiq.reward.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param issuer     the {@code iss} claim, checked on every incoming token
 * @param accessTtl  short by design: a stolen access token cannot be revoked, only outlived
 * @param refreshTtl how long a login survives without use
 * @param privateKey base64 PKCS#8 RSA private key. Blank in development, where an ephemeral key
 *                   is generated at startup; required in staging and production
 * @param publicKey  base64 X.509 RSA public key, the pair of the above
 */
@ConfigurationProperties(prefix = "intiq.jwt")
public record JwtProperties(String issuer,
                            Duration accessTtl,
                            Duration refreshTtl,
                            String privateKey,
                            String publicKey) {
}
