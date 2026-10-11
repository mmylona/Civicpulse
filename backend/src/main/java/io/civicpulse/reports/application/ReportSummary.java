package io.civicpulse.reports.application;

import io.civicpulse.reports.domain.Report;
import io.civicpulse.reports.domain.ReportStatus;
import io.civicpulse.reports.domain.Severity;
import java.time.Instant;
import java.util.UUID;

public record ReportSummary(
        UUID id,
        String reference,
        String title,
        String categoryCode,
        ReportStatus status,
        Severity severity,
        double latitude,
        double longitude,
        int stillPresentCount,
        Instant createdAt,
        Instant updatedAt) {

    static ReportSummary of(Report report) {
        return new ReportSummary(
                report.id(),
                report.reference(),
                report.title(),
                report.categoryCode(),
                report.status(),
                report.severity(),
                report.latitude(),
                report.longitude(),
                report.stillPresentCount(),
                report.createdAt(),
                report.updatedAt());
    }
}
