package io.civicpulse.reports.domain;

public enum TimelineType {
    SUBMITTED,
    EDITED,
    STATUS_CHANGED,
    // staff only text, e.g. why a report was handed back
    NOTE
}
