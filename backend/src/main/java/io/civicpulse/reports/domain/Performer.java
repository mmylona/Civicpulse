package io.civicpulse.reports.domain;

public enum Performer {
    MODERATOR,
    OPERATOR,
    // operator whose team the report is assigned to
    TEAM_OPERATOR,
    REPORTER,
    SYSTEM
}
