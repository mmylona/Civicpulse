package io.civicpulse.identity;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// read-only lookups for other modules
public interface UserDirectory {

    Optional<UserSummary> findUser(UUID id);

    Optional<TeamSummary> findTeam(UUID id);

    Map<UUID, String> displayNames(Collection<UUID> ids);
}
