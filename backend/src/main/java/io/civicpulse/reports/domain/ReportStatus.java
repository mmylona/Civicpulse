package io.civicpulse.reports.domain;

public enum ReportStatus {
    REPORTED,
    TRIAGED,
    ASSIGNED,
    IN_PROGRESS,
    WAITING_FOR_INFORMATION,
    RESOLVED,
    CLOSED,
    REJECTED,
    DUPLICATE;

    // no more comments or confirmations
    public boolean isClosed() {
        return this == CLOSED || this == REJECTED || this == DUPLICATE;
    }
}
