package io.civicpulse.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEntry(
        UUID id,
        Instant occurredAt,
        UUID actorId,
        String action,
        String targetType,
        String targetId,
        Map<String, Object> details,
        String requestId) {
}
