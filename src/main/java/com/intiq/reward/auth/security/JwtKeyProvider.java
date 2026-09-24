package com.intiq.reward.auth.security;

import com.intiq.reward.auth.config.JwtProperties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Supplies the RSA key pair used to sign and verify access tokens.
 *
 * <p>Configured keys are used when present. Otherwise a pair is generated at startup, which keeps
 * local development free of setup: the cost is that every restart invalidates issued tokens, so a
 * missing key in production is refused rather than silently generated.
 */
@Component
@Getter
@Slf4j
public class JwtKeyProvider {

    private static final String KEY_ID = "intiq-access";

    private final RSAPublicKey publicKey;
    private final RSAPrivateKey privateKey;
    private final String keyId = KEY_ID;

    public JwtKeyProvider(JwtProperties properties) {
        if (StringUtils.hasText(properties.privateKey()) && StringUtils.hasText(properties.publicKey())) {
            this.privateKey = readPrivateKey(properties.privateKey());
            this.publicKey = readPublicKey(properties.publicKey());
            log.info("JWT signing key loaded from configuration");
        } else {
            KeyPair generated = generate();
            this.privateKey = (RSAPrivateKey) generated.getPrivate();
            this.publicKey = (RSAPublicKey) generated.getPublic();
            log.warn("No JWT key configured: generated an ephemeral one. "
                    + "Every restart will invalidate existing sessions. Set INTIQ_JWT_PRIVATE_KEY outside development.");
        }
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Could not generate an RSA key pair", e);
        }
    }

    private static RSAPrivateKey readPrivateKey(String base64Pkcs8) {
        try {
            byte[] der = Base64.getDecoder().decode(stripPem(base64Pkcs8));
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("intiq.jwt.private-key is not a valid base64 PKCS#8 RSA key", e);
        }
    }

    private static RSAPublicKey readPublicKey(String base64X509) {
        try {
            byte[] der = Base64.getDecoder().decode(stripPem(base64X509));
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("intiq.jwt.public-key is not a valid base64 X.509 RSA key", e);
        }
    }

    /** Accepts either a bare base64 blob or a full PEM block. */
    private static String stripPem(String value) {
        return value.replaceAll("-----(BEGIN|END)[^-]+-----", "").replaceAll("\\s", "");
    }
}
