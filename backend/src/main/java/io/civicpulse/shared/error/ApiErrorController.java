package io.civicpulse.shared.error;

import io.civicpulse.shared.web.RequestIdFilter;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Spring Boot's default {@code /error} endpoint so that errors raised outside
 * controllers (servlet container, filters) use the same {@link ApiError} shape instead of
 * the framework's default body.
 */
@RestController
public class ApiErrorController implements ErrorController {

    private final ApiErrorResponses responses;

    public ApiErrorController(ApiErrorResponses responses) {
        this.responses = responses;
    }

    @RequestMapping("${server.error.path:/error}")
    ResponseEntity<ApiError> error(HttpServletRequest request) {
        HttpStatusCode status = statusOf(request);
        ErrorCode code = CommonErrorCode.forStatus(status.value());
        Object uri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String path = uri instanceof String s ? s : request.getRequestURI();
        return responses.of(code, status, code.defaultMessage(), path, RequestIdFilter.currentId(request), List.of());
    }

    private static HttpStatusCode statusOf(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (status instanceof Integer value && value >= 400 && value <= 599) {
            return HttpStatusCode.valueOf(value);
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
