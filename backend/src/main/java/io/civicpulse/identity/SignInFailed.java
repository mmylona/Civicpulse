package io.civicpulse.identity;

import java.time.Instant;
import java.util.UUID;

public record SignInFailed(UUID userId, String email, boolean accountLocked, Instant occurredAt) {
}
