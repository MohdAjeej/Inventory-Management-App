package com.b2binventory.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    public record TokenClaims(Long userId, Long membershipId, Long businessId) {
    }

    private final SecretKey key;
    private final Duration accessTtl;

    public JwtService(JwtProperties props) {
        if (props.secret() == null || props.secret().isBlank()) {
            throw new IllegalStateException("JWT_SECRET is not configured. Set it in the environment or .env file.");
        }
        byte[] bytes;
        try {
            bytes = Decoders.BASE64.decode(props.secret());
        } catch (RuntimeException e) {
            bytes = props.secret().getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.accessTtl = Duration.ofMinutes(props.accessTokenMinutes());
    }

    public String createAccessToken(Long userId, Long membershipId, Long businessId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("mid", membershipId)
                .claim("bid", businessId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    public long accessTokenSeconds() {
        return accessTtl.toSeconds();
    }

    public Optional<TokenClaims> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new TokenClaims(
                    Long.valueOf(claims.getSubject()),
                    claims.get("mid", Number.class).longValue(),
                    claims.get("bid", Number.class).longValue()));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
