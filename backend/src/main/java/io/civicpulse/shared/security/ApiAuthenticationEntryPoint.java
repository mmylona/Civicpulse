package io.civicpulse.shared.security;

import io.civicpulse.shared.error.ApiErrorResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ApiErrorResponses errors;

    ApiAuthenticationEntryPoint(ApiErrorResponses errors) {
        this.errors = errors;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        if (exception instanceof OAuth2AuthenticationException) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
            errors.write(request, response, SecurityErrorCode.TOKEN_INVALID);
        } else {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            errors.write(request, response, SecurityErrorCode.AUTHENTICATION_REQUIRED);
        }
    }
}
