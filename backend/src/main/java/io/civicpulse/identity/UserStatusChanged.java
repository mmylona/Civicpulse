package io.civicpulse.identity;

import java.time.Instant;
import java.util.UUID;

public record UserStatusChanged(UUID userId, String email, boolean enabled, UUID changedBy, Instant occurredAt) {
}
