package io.civicpulse.shared.error;

import java.util.Objects;

// The message is returned to the client, so it must not contain internal details.
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
