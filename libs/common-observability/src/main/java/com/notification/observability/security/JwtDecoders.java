package com.notification.observability.security;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Builds an HMAC-SHA256 JwtDecoder from a shared secret. There is no login endpoint
 * or auth server anywhere in this system (PRD's API list doesn't have one) - JWTs
 * are issued out-of-band to trusted clients using this same secret, and every
 * service just validates the signature. Spring Boot's built-in oauth2-resourceserver
 * auto-configuration expects an issuer-uri or jwk-set-uri (asymmetric-key setups);
 * a plain shared-secret decoder has to be built manually like this instead.
 */
public final class JwtDecoders {

    private JwtDecoders() {
    }

    public static JwtDecoder hmac256(String secret) {
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }
}
