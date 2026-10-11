package io.civicpulse.reports.domain;

import io.civicpulse.shared.security.Role;
import java.util.Set;
import java.util.UUID;

public record Actor(UUID id, Set<Role> roles, UUID teamId) {

    private static final Actor SYSTEM = new Actor(null, Set.of(), null);

    public Actor {
        roles = Set.copyOf(roles);
    }

    public static Actor system() {
        return SYSTEM;
    }

    public boolean isSystem() {
        return id == null;
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public boolean isStaff() {
        return hasRole(Role.MODERATOR) || hasRole(Role.CITY_OPERATOR);
    }
}
