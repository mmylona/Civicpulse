package io.civicpulse.identity.application;

import io.civicpulse.identity.RefreshTokenReuseDetected;
import io.civicpulse.identity.domain.RefreshToken;
import io.civicpulse.identity.infrastructure.RefreshTokenRepository;
import io.civicpulse.shared.Ids;
import io.civicpulse.shared.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository tokens;
    private final RefreshTokenProperties properties;
    private final ApplicationEventPublisher events;

    RefreshTokenService(RefreshTokenRepository tokens, RefreshTokenProperties properties,
            ApplicationEventPublisher events) {
        this.tokens = tokens;
        this.properties = properties;
        this.events = events;
    }

    String startFamily(UUID userId, Instant now) {
        return issue(Ids.newId(), userId, Ids.newId(), now);
    }

    Rotation rotate(String rawToken, Instant now) {
        RefreshToken current = tokens.lockByTokenHash(hash(rawToken))
                .orElseThrow(() -> new ApiException(IdentityErrorCode.INVALID_REFRESH_TOKEN));

        if (current.isRevoked()) {
            if (!current.wasRotated()) {
                throw new ApiException(IdentityErrorCode.INVALID_REFRESH_TOKEN);
            }
            if (current.wasRotatedWithin(properties.reuseGrace(), now)) {
                throw new ApiException(IdentityErrorCode.REFRESH_TOKEN_ROTATED);
            }
            tokens.revokeFamily(current.familyId(), now);
            log.warn("Refresh token reuse detected for user {}; token family {} revoked",
                    current.userId(), current.familyId());
            events.publishEvent(new RefreshTokenReuseDetected(current.userId(), current.familyId(), now));
            throw new ApiException(IdentityErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (current.isExpiredAt(now)) {
            throw new ApiException(IdentityErrorCode.INVALID_REFRESH_TOKEN);
        }

        UUID successorId = Ids.newId();
        current.rotate(successorId, now);
        String successor = issue(successorId, current.userId(), current.familyId(), now);
        return new Rotation(current.userId(), current.familyId(), successor);
    }

    void revokeFamily(UUID familyId, Instant now) {
        tokens.revokeFamily(familyId, now);
    }

    void revokeFamilyOf(String rawToken, Instant now) {
        tokens.findByTokenHash(hash(rawToken)).ifPresent(token -> tokens.revokeFamily(token.familyId(), now));
    }

    void revokeAllForUser(UUID userId, Instant now) {
        tokens.revokeAllForUser(userId, now);
    }

    private String issue(UUID id, UUID userId, UUID familyId, Instant now) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(new RefreshToken(id, userId, familyId, hash(raw), now, now.plus(properties.ttl())));
        return raw;
    }

    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    record Rotation(UUID userId, UUID familyId, String refreshToken) {
    }
}
