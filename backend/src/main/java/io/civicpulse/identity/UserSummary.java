package io.civicpulse.identity;

import io.civicpulse.shared.security.Role;
import java.util.Set;
import java.util.UUID;

public record UserSummary(UUID id, String displayName, Set<Role> roles, UUID teamId, boolean enabled) {

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }
}
