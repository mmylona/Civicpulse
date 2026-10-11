package io.civicpulse.reports.domain;

import io.civicpulse.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ReportErrorCode implements ErrorCode {

    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "Report was not found"),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Comment was not found"),
    UNKNOWN_CATEGORY(HttpStatus.UNPROCESSABLE_ENTITY, "Category does not exist"),
    LOCATION_OUTSIDE_CITY(HttpStatus.UNPROCESSABLE_ENTITY, "The location is outside the city"),
    INVALID_BOUNDING_BOX(HttpStatus.BAD_REQUEST, "Invalid bbox, or too large for the zoom level"),
    UNKNOWN_TEAM(HttpStatus.UNPROCESSABLE_ENTITY, "Team does not exist"),
    ASSIGNEE_NOT_IN_TEAM(HttpStatus.UNPROCESSABLE_ENTITY, "The assignee must be a city operator in the team"),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "This action is not possible in the report's current status"),
    REPORT_NOT_EDITABLE(HttpStatus.CONFLICT, "The report can only be edited before it is triaged"),
    REOPEN_WINDOW_CLOSED(HttpStatus.CONFLICT, "The report was resolved too long ago to be reopened"),
    DUPLICATE_TARGET_INVALID(HttpStatus.CONFLICT, "The report can only be marked as a duplicate of another open report"),
    REPORT_CLOSED(HttpStatus.CONFLICT, "The report is closed"),
    OWN_REPORT(HttpStatus.CONFLICT, "You cannot confirm your own report");

    private final HttpStatus status;
    private final String defaultMessage;

    ReportErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
