package io.civicpulse.reports.domain;

public record ReportContent(
        String categoryCode,
        String title,
        String description,
        Severity severity,
        double latitude,
        double longitude,
        String address) {
}
