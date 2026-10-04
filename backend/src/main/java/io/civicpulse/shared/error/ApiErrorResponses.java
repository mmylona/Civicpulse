package io.civicpulse.shared.error;

import io.civicpulse.shared.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class ApiErrorResponses {

    private final Clock clock;

    public ApiErrorResponses(Clock clock) {
        this.clock = clock;
    }

    public ResponseEntity<ApiError> of(ErrorCode code, HttpServletRequest request) {
        return of(code, code.status(), code.defaultMessage(), request, List.of());
    }

    public ResponseEntity<ApiError> of(ErrorCode code, String safeMessage, HttpServletRequest request) {
        return of(code, code.status(), safeMessage, request, List.of());
    }

    public ResponseEntity<ApiError> of(
            ErrorCode code,
            HttpStatusCode status,
            String safeMessage,
            HttpServletRequest request,
            List<ApiError.FieldViolation> errors) {
        return of(code, status, safeMessage, request.getRequestURI(), RequestIdFilter.currentId(request), errors);
    }

    public ResponseEntity<ApiError> of(
            ErrorCode code,
            HttpStatusCode status,
            String safeMessage,
            String path,
            String traceId,
            List<ApiError.FieldViolation> errors) {
        ApiError body = new ApiError(
                Instant.now(clock), status.value(), code.code(), safeMessage, path, traceId, errors);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
