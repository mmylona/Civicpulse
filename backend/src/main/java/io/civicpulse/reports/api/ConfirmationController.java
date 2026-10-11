package io.civicpulse.reports.api;

import io.civicpulse.reports.application.ConfirmationService;
import io.civicpulse.reports.application.ConfirmationView;
import io.civicpulse.reports.domain.ConfirmationKind;
import io.civicpulse.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports/{id}/confirmation")
class ConfirmationController {

    private final ConfirmationService confirmations;

    ConfirmationController(ConfirmationService confirmations) {
        this.confirmations = confirmations;
    }

    @PutMapping
    ConfirmationView confirm(@PathVariable UUID id, @Valid @RequestBody ConfirmationRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        return confirmations.confirm(id, body.kind(), CurrentUser.from(jwt));
    }

    @DeleteMapping
    ConfirmationView withdraw(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return confirmations.withdraw(id, CurrentUser.from(jwt));
    }

    record ConfirmationRequest(@NotNull ConfirmationKind kind) {
    }
}
