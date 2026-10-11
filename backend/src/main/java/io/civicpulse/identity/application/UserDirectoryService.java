package io.civicpulse.identity.application;

import io.civicpulse.identity.TeamSummary;
import io.civicpulse.identity.UserDirectory;
import io.civicpulse.identity.UserSummary;
import io.civicpulse.identity.domain.EmailAddresses;
import io.civicpulse.identity.domain.User;
import io.civicpulse.identity.infrastructure.TeamRepository;
import io.civicpulse.identity.infrastructure.UserRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class UserDirectoryService implements UserDirectory {

    private final UserRepository users;
    private final TeamRepository teams;

    UserDirectoryService(UserRepository users, TeamRepository teams) {
        this.users = users;
        this.teams = teams;
    }

    @Override
    public Optional<UserSummary> findUser(UUID id) {
        return users.findById(id).map(UserDirectoryService::summary);
    }

    @Override
    public Optional<UserSummary> findUserByEmail(String email) {
        return users.findByEmail(EmailAddresses.normalize(email)).map(UserDirectoryService::summary);
    }

    @Override
    public List<TeamSummary> teams() {
        return teams.findAllByOrderByNameAsc().stream()
                .map(team -> new TeamSummary(team.getId(), team.name()))
                .toList();
    }

    @Override
    public Optional<TeamSummary> findTeam(UUID id) {
        return teams.findById(id).map(team -> new TeamSummary(team.getId(), team.name()));
    }

    @Override
    public Map<UUID, String> displayNames(Collection<UUID> ids) {
        Map<UUID, String> names = new HashMap<>();
        if (!ids.isEmpty()) {
            users.findAllById(ids).forEach(user -> names.put(user.id(), user.displayName()));
        }
        return names;
    }

    private static UserSummary summary(User user) {
        UUID teamId = user.team() == null ? null : user.team().getId();
        return new UserSummary(user.id(), user.displayName(), user.roles(), teamId, user.isEnabled());
    }
}
