package io.civicpulse.reports.application;

import io.civicpulse.reports.domain.ActorKind;
import io.civicpulse.reports.domain.TimelineType;
import io.civicpulse.reports.domain.Visibility;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

// actorName is only filled in for staff
public record TimelineEntry(
        UUID id,
        TimelineType type,
        Visibility visibility,
        ActorKind actorKind,
        String actorName,
        Map<String, Object> details,
        Instant occurredAt) {
}
