package io.civicpulse.reports.application;

import io.civicpulse.reports.domain.ActorKind;
import io.civicpulse.reports.domain.Visibility;
import java.time.Instant;
import java.util.UUID;

// body is null for a hidden comment unless the viewer is staff
public record CommentView(
        UUID id,
        ActorKind authorKind,
        String authorName,
        boolean mine,
        Visibility visibility,
        String body,
        boolean hidden,
        String hiddenReason,
        Instant createdAt) {
}
