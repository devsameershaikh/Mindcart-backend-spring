package com.mindcart.backend.security;

import com.mindcart.backend.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

@Component
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-days:30}")
    private long expirationDays;

    @Value("${app.jwt.issuer:mindcart-backend}")
    private String issuer;

    private SecretKey key;

    @PostConstruct
    void init() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET is not set. Refusing to start with no/blank signing key — " +
                    "generate one with e.g. `openssl rand -base64 48` and set it as an env var.");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            // HS256 requires a key of at least 256 bits (32 bytes). A short/weak
            // secret is brute-forceable; the original Node code never checked
            // this, so a misconfigured JWT_SECRET there could go unnoticed.
            throw new IllegalStateException(
                    "JWT_SECRET is too short (" + bytes.length + " bytes). It must be at least 32 bytes " +
                    "(256 bits) for HS256. Generate one with `openssl rand -base64 48`.");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public String signSession(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + Duration.ofDays(expirationDays).toMillis());
        return Jwts.builder()
                .subject(user.getId())
                .claim("email", user.getEmail())
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /** Throws JwtException/IllegalArgumentException on anything invalid or expired. */
    public Claims verify(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public static class InvalidTokenException extends JwtException {
        public InvalidTokenException(String message) {
            super(message);
        }
    }
}
