package io.civicpulse.shared.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.civicpulse.shared.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
    private final ObjectMapper objectMapper;

    public ApiErrorResponses(Clock clock, ObjectMapper objectMapper) {
        this.clock = clock;
        this.objectMapper = objectMapper;
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

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code) throws IOException {
        ApiError body = new ApiError(
                Instant.now(clock),
                code.status().value(),
                code.code(),
                code.defaultMessage(),
                request.getRequestURI(),
                RequestIdFilter.currentId(request),
                List.of());
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
