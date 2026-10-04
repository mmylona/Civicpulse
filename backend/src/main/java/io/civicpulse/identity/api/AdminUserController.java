package io.civicpulse.identity.api;

import io.civicpulse.identity.application.AdminUserView;
import io.civicpulse.identity.application.UserAdministrationService;
import io.civicpulse.identity.application.UserProfile;
import io.civicpulse.shared.security.CurrentUser;
import io.civicpulse.shared.security.Role;
import io.civicpulse.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
class AdminUserController {

    private final UserAdministrationService administration;

    AdminUserController(UserAdministrationService administration) {
        this.administration = administration;
    }

    @GetMapping("/users")
    PageResponse<AdminUserView> users(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) Role role,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return administration.search(q, role, page, size);
    }

    @PutMapping("/users/{id}/roles")
    AdminUserView changeRoles(@PathVariable UUID id, @Valid @RequestBody RolesRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        return administration.changeRoles(id, body.roles(), CurrentUser.from(jwt));
    }

    @PutMapping("/users/{id}/status")
    AdminUserView changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest body,
            @AuthenticationPrincipal Jwt jwt) {
        return administration.changeStatus(id, body.enabled(), CurrentUser.from(jwt));
    }

    @PutMapping("/users/{id}/team")
    AdminUserView changeTeam(@PathVariable UUID id, @RequestBody TeamRequest body, @AuthenticationPrincipal Jwt jwt) {
        return administration.changeTeam(id, body.teamId(), CurrentUser.from(jwt));
    }

    @GetMapping("/teams")
    List<UserProfile.TeamView> teams() {
        return administration.teams();
    }

    record RolesRequest(@NotEmpty Set<@NotNull Role> roles) {
    }

    record StatusRequest(@NotNull Boolean enabled) {
    }

    record TeamRequest(UUID teamId) {
    }
}
