package io.civicpulse.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.civicpulse.shared.security.Role;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");

    private final User user = User.register(UUID.randomUUID(), "ada@example.org", "hash", "Ada", NOW);

    @Test
    void newUserIsEnabledCitizen() {
        assertThat(user.roles()).containsExactly(Role.CITIZEN);
        assertThat(user.canSignInAt(NOW)).isTrue();
    }

    @Test
    void lockAfterFiveFailures() {
        for (int i = 1; i < User.MAX_FAILED_LOGINS; i++) {
            assertThat(user.recordFailedLogin(NOW)).isFalse();
        }

        assertThat(user.recordFailedLogin(NOW)).isTrue();
        assertThat(user.canSignInAt(NOW.plus(Duration.ofMinutes(14)))).isFalse();
        assertThat(user.canSignInAt(NOW.plus(Duration.ofMinutes(15)))).isTrue();
    }

    @Test
    void successfulLoginResetsFailures() {
        user.recordFailedLogin(NOW);
        user.recordFailedLogin(NOW);

        user.recordSuccessfulLogin(NOW);

        assertThat(user.failedLoginCount()).isZero();
        assertThat(user.lastLoginAt()).isEqualTo(NOW);
    }

    @Test
    void disabledUserCannotSignIn() {
        user.changeEnabled(false);

        assertThat(user.canSignInAt(NOW)).isFalse();
    }

    @Test
    void replaceRoles() {
        assertThat(user.replaceRoles(Set.of(Role.CITIZEN, Role.MODERATOR))).isTrue();
        assertThat(user.replaceRoles(Set.of(Role.CITIZEN, Role.MODERATOR))).isFalse();
        assertThat(user.roles()).containsExactlyInAnyOrder(Role.CITIZEN, Role.MODERATOR);

        assertThatThrownBy(() -> user.replaceRoles(Set.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizeEmail() {
        assertThat(EmailAddresses.normalize("  Ada.Lovelace@Example.ORG ")).isEqualTo("ada.lovelace@example.org");
    }
}
