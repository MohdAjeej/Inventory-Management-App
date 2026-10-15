package com.b2binventory.service;

import com.b2binventory.entity.Membership;
import com.b2binventory.entity.RefreshToken;
import com.b2binventory.exception.UnauthorizedException;
import com.b2binventory.repository.RefreshTokenRepository;
import com.b2binventory.security.JwtProperties;
import com.b2binventory.security.JwtService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class TokenService {

    public record IssuedTokens(String accessToken, String refreshToken, long expiresIn) {
    }

    private final SecureRandom random = new SecureRandom();
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokens;
    private final Duration refreshTtl;

    public TokenService(JwtService jwtService, RefreshTokenRepository refreshTokens, JwtProperties props) {
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.refreshTtl = Duration.ofDays(props.refreshTokenDays());
    }

    @Transactional
    public IssuedTokens issue(Membership membership) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken token = new RefreshToken();
        token.setMembership(membership);
        token.setTokenHash(hash(raw));
        token.setExpiresAt(Instant.now().plus(refreshTtl));
        refreshTokens.save(token);

        String access = jwtService.createAccessToken(membership.getUser().getId(), membership.getId(), membership.getBusinessId());
        return new IssuedTokens(access, raw, jwtService.accessTokenSeconds());
    }

    /** Validates and revokes the presented refresh token (rotation), returning its membership. */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public Membership consume(String rawToken) {
        RefreshToken token = refreshTokens.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (token.isRevoked()) {
            // A revoked token being replayed suggests theft: revoke the whole session family.
            refreshTokens.revokeAllForMembership(token.getMembership().getId());
            throw new UnauthorizedException("Refresh token has been revoked");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Refresh token has expired");
        }
        token.setRevoked(true);
        Membership membership = token.getMembership();
        if (!membership.isActive()) {
            throw new UnauthorizedException("Your account has been deactivated");
        }
        return membership;
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokens.findByTokenHash(hash(rawToken)).ifPresent(t -> t.setRevoked(true));
    }

    @Transactional
    public void revokeAll(Long membershipId) {
        refreshTokens.revokeAllForMembership(membershipId);
    }

    private static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
