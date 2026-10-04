package io.civicpulse.identity.application;

import io.civicpulse.identity.domain.Team;
import io.civicpulse.identity.domain.User;
import io.civicpulse.shared.security.Role;
import java.util.List;
import java.util.UUID;

public record UserProfile(UUID id, String email, String displayName, List<Role> roles, TeamView team) {

    static UserProfile of(User user) {
        return new UserProfile(user.id(), user.email(), user.displayName(), sorted(user), TeamView.of(user.team()));
    }

    static List<Role> sorted(User user) {
        return user.roles().stream().sorted().toList();
    }

    public record TeamView(UUID id, String name) {

        static TeamView of(Team team) {
            return team == null ? null : new TeamView(team.getId(), team.name());
        }
    }
}
