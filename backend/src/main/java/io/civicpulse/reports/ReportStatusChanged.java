package io.civicpulse.reports;

import java.time.Instant;
import java.util.UUID;

// actorId is null when the system made the change
public record ReportStatusChanged(UUID reportId, String reference, String action, String from, String to,
        UUID actorId, Instant occurredAt) {
}
