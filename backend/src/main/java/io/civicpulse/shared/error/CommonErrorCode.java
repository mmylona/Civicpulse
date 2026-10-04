package io.civicpulse.shared.error;

import org.springframework.http.HttpStatus;

public enum CommonErrorCode implements ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "The request contains invalid values"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "The request could not be read"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested resource was not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "This method is not supported for this resource"),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "The requested response format is not supported"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "This content type is not supported"),
    REQUEST_REJECTED(HttpStatus.BAD_REQUEST, "The request was rejected"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");

    private final HttpStatus status;
    private final String defaultMessage;

    CommonErrorCode(HttpStatus status, String defaultMessage) {
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

    static ErrorCode forStatus(int status) {
        return switch (status) {
            case 400 -> MALFORMED_REQUEST;
            case 404 -> RESOURCE_NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 406 -> NOT_ACCEPTABLE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            default -> status >= 400 && status < 500 ? REQUEST_REJECTED : INTERNAL_ERROR;
        };
    }
}
