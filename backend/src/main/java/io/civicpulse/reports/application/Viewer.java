package io.civicpulse.reports.application;

import io.civicpulse.reports.domain.Actor;
import io.civicpulse.shared.security.Role;
import java.util.Set;
import java.util.UUID;

public record Viewer(UUID userId, Set<Role> roles) {

    private static final Viewer ANONYMOUS = new Viewer(null, Set.of());

    public Viewer {
        roles = Set.copyOf(roles);
    }

    public static Viewer anonymous() {
        return ANONYMOUS;
    }

    public static Viewer of(Actor actor) {
        return actor.isSystem() ? ANONYMOUS : new Viewer(actor.id(), actor.roles());
    }

    public boolean isSignedIn() {
        return userId != null;
    }

    public boolean isStaff() {
        return roles.contains(Role.MODERATOR) || roles.contains(Role.CITY_OPERATOR) || roles.contains(Role.ADMIN);
    }

    public boolean is(UUID id) {
        return userId != null && userId.equals(id);
    }
}
