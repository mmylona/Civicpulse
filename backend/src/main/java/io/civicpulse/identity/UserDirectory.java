package io.civicpulse.identity;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// read-only lookups for other modules
public interface UserDirectory {

    Optional<UserSummary> findUser(UUID id);

    Optional<UserSummary> findUserByEmail(String email);

    Optional<TeamSummary> findTeam(UUID id);

    List<TeamSummary> teams();

    Map<UUID, String> displayNames(Collection<UUID> ids);
}
