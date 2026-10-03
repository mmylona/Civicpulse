package io.civicpulse.shared.error;

import java.util.Objects;

/**
 * Base class for expected business failures (not found, invalid transition, conflict…).
 *
 * <p>The message must be safe to return to the caller: it ends up in the error body.
 * Never put exception messages, SQL or internal identifiers that callers must not see
 * into it.
 */
public class ApiException extends RuntimeException {

    private final transient ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public ApiException(ErrorCode errorCode, String safeMessage) {
        super(safeMessage);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
