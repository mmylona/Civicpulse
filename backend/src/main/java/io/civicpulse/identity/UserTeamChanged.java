package io.civicpulse.identity;

import java.time.Instant;
import java.util.UUID;

public record UserTeamChanged(UUID userId, String email, UUID teamId, UUID changedBy, Instant occurredAt) {
}
