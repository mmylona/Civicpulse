package io.civicpulse.shared.error;

import java.time.Instant;
import java.util.List;

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

    public record FieldViolation(String field, String code, String message) {
    }
}
