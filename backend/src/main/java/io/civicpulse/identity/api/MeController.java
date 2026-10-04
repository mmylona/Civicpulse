package io.civicpulse.identity.api;

import io.civicpulse.identity.application.AuthService;
import io.civicpulse.identity.application.UserProfile;
import io.civicpulse.shared.security.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController {

    private final AuthService auth;

    MeController(AuthService auth) {
        this.auth = auth;
    }

    @GetMapping("/api/v1/me")
    UserProfile me(@AuthenticationPrincipal Jwt jwt) {
        return auth.profile(CurrentUser.from(jwt).id());
    }
}
