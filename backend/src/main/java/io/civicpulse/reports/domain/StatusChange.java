package io.civicpulse.reports.domain;

public record StatusChange(ReportAction action, ReportStatus from, ReportStatus to) {
}
