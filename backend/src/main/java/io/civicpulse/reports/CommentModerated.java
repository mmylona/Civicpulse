package io.civicpulse.reports;

import java.time.Instant;
import java.util.UUID;

public record CommentModerated(UUID reportId, UUID commentId, boolean hidden, String reason, UUID moderatorId,
        Instant occurredAt) {
}
