package io.civicpulse.identity;

import java.time.Instant;
import java.util.UUID;

public record UserRegistered(UUID userId, String email, Instant occurredAt) {
}
