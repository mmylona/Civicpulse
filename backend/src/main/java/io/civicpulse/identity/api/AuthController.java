package io.civicpulse.identity.api;

import io.civicpulse.identity.application.AuthResult;
import io.civicpulse.identity.application.AuthService;
import io.civicpulse.shared.web.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private static final int LOGINS_PER_MINUTE = 10;
    private static final int REGISTRATIONS_PER_HOUR = 10;

    private final AuthService auth;
    private final RefreshCookies cookies;
    private final RateLimiter rateLimiter;

    AuthController(AuthService auth, RefreshCookies cookies, RateLimiter rateLimiter) {
        this.auth = auth;
        this.cookies = cookies;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/register")
    ResponseEntity<TokenResponse> register(@Valid @RequestBody AuthRequests.Register body, HttpServletRequest request) {
        rateLimiter.check("register", request.getRemoteAddr(), REGISTRATIONS_PER_HOUR, Duration.ofHours(1));
        AuthResult result = auth.register(body.email(), body.password(), body.displayName());
        return withSession(HttpStatus.CREATED, result);
    }

    @PostMapping("/login")
    ResponseEntity<TokenResponse> login(@Valid @RequestBody AuthRequests.Login body, HttpServletRequest request) {
        rateLimiter.check("login", request.getRemoteAddr(), LOGINS_PER_MINUTE, Duration.ofMinutes(1));
        return withSession(HttpStatus.OK, auth.login(body.email(), body.password()));
    }

    @PostMapping("/refresh")
    ResponseEntity<TokenResponse> refresh(@CookieValue(name = RefreshCookies.NAME, required = false) String token) {
        return withSession(HttpStatus.OK, auth.refresh(token));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = RefreshCookies.NAME, required = false) String token) {
        auth.logout(token);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear()).build();
    }

    private ResponseEntity<TokenResponse> withSession(HttpStatus status, AuthResult result) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookies.issue(result.refreshToken()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(TokenResponse.of(result));
    }
}
