package io.civicpulse.reports;

import java.time.Instant;
import java.util.UUID;

public record ReportSubmitted(UUID reportId, String reference, UUID reporterId, String categoryCode,
        Instant occurredAt) {
}
