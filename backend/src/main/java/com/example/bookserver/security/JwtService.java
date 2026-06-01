package com.example.bookserver.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Issues and validates HS256 JWT tokens used by the {@code /api/auth/*} flow.
 * <p>
 * Token types:
 * <ul>
 *     <li>{@code access} — short-lived, accepted by {@link JwtAuthenticationFilter}.</li>
 *     <li>{@code refresh} — longer-lived, accepted only by the refresh endpoint.</li>
 * </ul>
 * The token type is carried in the {@code typ} private claim.
 */
@Service
public class JwtService {

    public static final String CLAIM_TYPE = "typ";
    public static final String CLAIM_ROLES = "roles";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final String secret;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private SecretKey signingKey;

    public JwtService(
            @Value("${app.security.jwt.secret}") String secret,
            @Value("${app.security.jwt.access-token-ttl:15m}") Duration accessTtl,
            @Value("${app.security.jwt.refresh-token-ttl:30d}") Duration refreshTtl) {
        this.secret = secret;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
    }

    @PostConstruct
    void init() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "app.security.jwt.secret is not configured. " +
                    "Set JWT_SECRET environment variable (>=32 chars).");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "app.security.jwt.secret must be at least 32 bytes (256 bits) for HS256.");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(UserPrincipal principal) {
        return buildToken(principal, TYPE_ACCESS, accessTtl);
    }

    public String generateRefreshToken(UserPrincipal principal) {
        return buildToken(principal, TYPE_REFRESH, refreshTtl);
    }

    public Duration getAccessTtl() {
        return accessTtl;
    }

    public Duration getRefreshTtl() {
        return refreshTtl;
    }

    private String buildToken(UserPrincipal principal, String type, Duration ttl) {
        Instant now = Instant.now();
        Instant exp = now.plus(ttl);
        return Jwts.builder()
                .subject(principal.getUsername())
                .claim(CLAIM_TYPE, type)
                .claim(CLAIM_ROLES, List.copyOf(principal.getRoleNames()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Optional<Claims> parseAndValidate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT validation failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public boolean isAccessToken(Claims claims) {
        return TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class));
    }
}
