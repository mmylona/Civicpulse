package io.civicpulse.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    private static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");
    private static final Duration GRACE = Duration.ofSeconds(10);

    private final RefreshToken token = new RefreshToken(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "hash", NOW, NOW.plus(Duration.ofDays(14)));

    @Test
    void expiry() {
        assertThat(token.isExpiredAt(NOW.plus(Duration.ofDays(14)).minusMillis(1))).isFalse();
        assertThat(token.isExpiredAt(NOW.plus(Duration.ofDays(14)))).isTrue();
    }

    @Test
    void graceWindowAfterRotation() {
        token.rotate(UUID.randomUUID(), NOW);

        assertThat(token.wasRotatedWithin(GRACE, NOW.plusSeconds(9))).isTrue();
        assertThat(token.wasRotatedWithin(GRACE, NOW.plusSeconds(10))).isFalse();
    }

    @Test
    void noGraceWithoutRotation() {
        assertThat(token.wasRotated()).isFalse();
        assertThat(token.wasRotatedWithin(GRACE, NOW)).isFalse();
    }

    @Test
    void cannotRotateTwice() {
        token.rotate(UUID.randomUUID(), NOW);

        assertThatThrownBy(() -> token.rotate(UUID.randomUUID(), NOW)).isInstanceOf(IllegalStateException.class);
    }
}
