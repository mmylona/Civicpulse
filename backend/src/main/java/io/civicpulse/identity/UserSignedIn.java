package io.civicpulse.identity;

import java.time.Instant;
import java.util.UUID;

public record UserSignedIn(UUID userId, String email, Instant occurredAt) {
}
