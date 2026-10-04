package io.civicpulse.identity;

import io.civicpulse.shared.security.Role;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserRolesChanged(
        UUID userId, String email, Set<Role> previousRoles, Set<Role> newRoles, UUID changedBy, Instant occurredAt) {
}
