package io.civicpulse.shared.security;

import io.civicpulse.shared.error.ApiErrorResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private final ApiErrorResponses errors;

    ApiAccessDeniedHandler(ApiErrorResponses errors) {
        this.errors = errors;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        errors.write(request, response, SecurityErrorCode.ACCESS_DENIED);
    }
}
