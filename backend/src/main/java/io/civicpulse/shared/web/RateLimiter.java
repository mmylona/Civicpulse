package io.civicpulse.shared.web;

import io.civicpulse.shared.error.RateLimitExceededException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

// in memory, per instance
@Component
public class RateLimiter {

    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Clock clock;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void check(String bucket, String key, int limit, Duration window) {
        Instant now = clock.instant();
        Window current = windows.compute(bucket + ':' + key, (id, existing) ->
                existing == null || !now.isBefore(existing.resetAt())
                        ? new Window(now.plus(window), 1)
                        : new Window(existing.resetAt(), existing.count() + 1));
        if (windows.size() > MAX_TRACKED_KEYS) {
            windows.values().removeIf(w -> !now.isBefore(w.resetAt()));
        }
        if (current.count() > limit) {
            throw new RateLimitExceededException(Duration.between(now, current.resetAt()));
        }
    }

    private record Window(Instant resetAt, int count) {
    }
}
