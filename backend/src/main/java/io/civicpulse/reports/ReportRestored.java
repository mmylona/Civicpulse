package io.civicpulse.reports;

import java.time.Instant;
import java.util.UUID;

public record ReportRestored(UUID reportId, String reference, String from, String reason, UUID moderatorId,
        Instant occurredAt) {
}
