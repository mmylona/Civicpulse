package io.civicpulse.shared.error;

import java.time.Instant;
import java.util.List;

/**
 * The single error body returned by the API for every failure (see docs/api/README.md).
 *
 * @param timestamp when the error was produced (UTC)
 * @param status the HTTP status code
 * @param code a stable machine-readable code
 * @param message a safe, human-readable summary
 * @param path the request path
 * @param traceId the request's correlation id ({@code X-Request-Id})
 * @param errors field-level validation errors; empty for non-validation failures
 */
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String traceId,
        List<FieldViolation> errors) {

    public ApiError {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /**
     * A single invalid field or parameter.
     *
     * @param field the field or parameter name, as the client sent it
     * @param code the violated constraint, e.g. {@code NotBlank}, {@code Size}
     * @param message a human-readable explanation
     */
    public record FieldViolation(String field, String code, String message) {
    }
}
