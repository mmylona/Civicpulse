package io.civicpulse.reports.application;

import io.civicpulse.reports.ReportStatusChanged;
import io.civicpulse.reports.ReportSubmitted;
import io.civicpulse.reports.domain.Actor;
import io.civicpulse.reports.domain.ActorKind;
import io.civicpulse.reports.domain.Report;
import io.civicpulse.reports.domain.StatusChange;
import io.civicpulse.reports.domain.TimelineType;
import io.civicpulse.reports.domain.Visibility;
import io.civicpulse.reports.infrastructure.ReportTimeline;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
class ReportHistory {

    private final ReportTimeline timeline;
    private final ApplicationEventPublisher events;

    ReportHistory(ReportTimeline timeline, ApplicationEventPublisher events) {
        this.timeline = timeline;
        this.events = events;
    }

    void submitted(Report report, Instant now) {
        timeline.append(report.id(), TimelineType.SUBMITTED, Visibility.PUBLIC, report.reporterId(), ActorKind.REPORTER,
                Map.of(), now);
        events.publishEvent(
                new ReportSubmitted(report.id(), report.reference(), report.reporterId(), report.categoryCode(), now));
    }

    void edited(Report report, Actor actor, List<String> fields, Instant now) {
        timeline.append(report.id(), TimelineType.EDITED, Visibility.PUBLIC, actor.id(), report.kindOf(actor),
                Map.of("fields", fields), now);
    }

    // details are public; internalDetails go into a separate staff-only entry
    void statusChanged(Report report, StatusChange change, Actor actor, Map<String, Object> details,
            Map<String, Object> internalDetails, Instant now) {
        ActorKind kind = report.kindOf(actor);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", change.action().name());
        payload.put("from", change.from().name());
        payload.put("to", change.to().name());
        payload.putAll(details);
        timeline.append(report.id(), TimelineType.STATUS_CHANGED, Visibility.PUBLIC, actor.id(), kind, payload, now);
        if (!internalDetails.isEmpty()) {
            Map<String, Object> note = new LinkedHashMap<>();
            note.put("action", change.action().name());
            note.putAll(internalDetails);
            timeline.append(report.id(), TimelineType.NOTE, Visibility.INTERNAL, actor.id(), kind, note, now);
        }
        events.publishEvent(new ReportStatusChanged(report.id(), report.reference(), change.action().name(),
                change.from().name(), change.to().name(), actor.id(), now));
    }
}
