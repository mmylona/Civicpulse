package io.civicpulse.identity.application;

import io.civicpulse.identity.domain.User;
import io.civicpulse.shared.security.Role;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminUserView(
        UUID id,
        String email,
        String displayName,
        List<Role> roles,
        UserProfile.TeamView team,
        boolean enabled,
        boolean locked,
        Instant lastLoginAt,
        Instant createdAt) {

    static AdminUserView of(User user, Instant now) {
        return new AdminUserView(
                user.id(),
                user.email(),
                user.displayName(),
                UserProfile.sorted(user),
                UserProfile.TeamView.of(user.team()),
                user.isEnabled(),
                user.isLockedAt(now),
                user.lastLoginAt(),
                user.createdAt());
    }
}
