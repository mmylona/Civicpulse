package io.civicpulse.reports.application;

import io.civicpulse.reports.domain.ConfirmationKind;
import io.civicpulse.reports.domain.ReportStatus;
import io.civicpulse.reports.domain.Severity;
import io.civicpulse.geo.NearbySite;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// staff is only set for moderators, operators and admins
public record ReportDetails(
        UUID id,
        String reference,
        String title,
        String description,
        String categoryCode,
        ReportStatus status,
        Severity severity,
        double latitude,
        double longitude,
        String address,
        District district,
        Link duplicateOf,
        int stillPresentCount,
        int fixedCount,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt,
        Instant closedAt,
        long version,
        boolean mine,
        ConfirmationKind myConfirmation,
        Staff staff) {

    public record Link(UUID id, String reference) {
    }

    public record District(String code, String name) {
    }

    public record Person(UUID id, String displayName) {
    }

    public record Team(UUID id, String name) {
    }

    public record Staff(
            Person reporter,
            Severity statedSeverity,
            Severity validatedSeverity,
            Team team,
            Person assignee,
            ReportStatus resumeStatus,
            boolean reopenRequested,
            List<NearbySite> criticalSitesNearby) {
    }
}
