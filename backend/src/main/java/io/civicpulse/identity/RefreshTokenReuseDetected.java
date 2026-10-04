package io.civicpulse.identity;

import java.time.Instant;
import java.util.UUID;

public record RefreshTokenReuseDetected(UUID userId, UUID familyId, Instant occurredAt) {
}
