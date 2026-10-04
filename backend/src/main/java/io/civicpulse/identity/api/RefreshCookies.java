package io.civicpulse.identity.api;

import io.civicpulse.identity.application.RefreshTokenProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
class RefreshCookies {

    static final String NAME = "civicpulse_refresh";
    private static final String PATH = "/api/v1/auth";

    private final RefreshTokenProperties properties;

    RefreshCookies(RefreshTokenProperties properties) {
        this.properties = properties;
    }

    String issue(String token) {
        return cookie(token, properties.ttl());
    }

    String clear() {
        return cookie("", Duration.ZERO);
    }

    private String cookie(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
