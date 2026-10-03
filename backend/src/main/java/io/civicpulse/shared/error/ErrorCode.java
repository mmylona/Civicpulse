package io.civicpulse.shared.error;

import org.springframework.http.HttpStatus;

/**
 * A stable, machine-readable error code with its HTTP status and a safe default message.
 *
 * <p>Each module declares its own enum implementing this interface (for example
 * {@code ReportErrorCode.REPORT_NOT_FOUND}). Codes are part of the public API contract:
 * clients and translations key on them, so renaming one is a breaking change.
 */
public interface ErrorCode {

    /** The code as it appears in the error body, e.g. {@code REPORT_NOT_FOUND}. */
    String code();

    HttpStatus status();

    /** An English message that is safe to show to any caller. */
    String defaultMessage();
}
