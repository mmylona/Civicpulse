package io.civicpulse.shared.security;

import io.civicpulse.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SecurityErrorCode implements ErrorCode {

    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "Sign in to continue"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "The access token is invalid or has expired"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "You do not have permission to do this");

    private final HttpStatus status;
    private final String defaultMessage;

    SecurityErrorCode(HttpStatus status, String defaultMessage) {
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
