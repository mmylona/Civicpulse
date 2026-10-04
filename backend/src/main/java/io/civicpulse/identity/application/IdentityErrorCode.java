package io.civicpulse.identity.application;

import io.civicpulse.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum IdentityErrorCode implements ErrorCode {

    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "An account with this email already exists"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED,
            "Email or password is incorrect, or the account is temporarily locked after too many attempts"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "The session has ended. Sign in again"),
    REFRESH_TOKEN_ROTATED(HttpStatus.UNAUTHORIZED, "The session was refreshed by another request. Try again"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User was not found"),
    UNKNOWN_TEAM(HttpStatus.UNPROCESSABLE_ENTITY, "Team does not exist"),
    CANNOT_REMOVE_OWN_ADMIN_ROLE(HttpStatus.CONFLICT, "You cannot remove the admin role from your own account"),
    CANNOT_DISABLE_OWN_ACCOUNT(HttpStatus.CONFLICT, "You cannot disable your own account");

    private final HttpStatus status;
    private final String defaultMessage;

    IdentityErrorCode(HttpStatus status, String defaultMessage) {
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
