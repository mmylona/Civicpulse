package io.civicpulse.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.civicpulse.shared.error.RateLimitExceededException;
import io.civicpulse.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-04T10:00:00Z"));
    private final RateLimiter limiter = new RateLimiter(clock);

    @Test
    void allowsUpToLimit() {
        for (int i = 0; i < 3; i++) {
            limiter.check("login", "10.0.0.1", 3, Duration.ofMinutes(1));
        }

        RateLimitExceededException exception = catchThrowableOfType(RateLimitExceededException.class,
                () -> limiter.check("login", "10.0.0.1", 3, Duration.ofMinutes(1)));

        assertThat(exception).isNotNull();
        assertThat(exception.retryAfterSeconds()).isEqualTo(60);
    }

    @Test
    void retryAfterRoundsUp() {
        limiter.check("login", "10.0.0.1", 1, Duration.ofMinutes(1));
        clock.advance(Duration.ofMillis(59_500));

        RateLimitExceededException exception = catchThrowableOfType(RateLimitExceededException.class,
                () -> limiter.check("login", "10.0.0.1", 1, Duration.ofMinutes(1)));

        assertThat(exception.retryAfterSeconds()).isEqualTo(1);
    }

    @Test
    void newWindowAfterExpiry() {
        limiter.check("login", "10.0.0.1", 1, Duration.ofMinutes(1));
        clock.advance(Duration.ofMinutes(1));

        assertThatCode(() -> limiter.check("login", "10.0.0.1", 1, Duration.ofMinutes(1))).doesNotThrowAnyException();
    }

    @Test
    void separateCountersPerKey() {
        limiter.check("login", "10.0.0.1", 1, Duration.ofMinutes(1));

        assertThatCode(() -> {
            limiter.check("login", "10.0.0.2", 1, Duration.ofMinutes(1));
            limiter.check("register", "10.0.0.1", 1, Duration.ofMinutes(1));
        }).doesNotThrowAnyException();
    }
}
