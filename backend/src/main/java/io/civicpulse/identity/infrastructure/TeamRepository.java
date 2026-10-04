package io.civicpulse.identity.infrastructure;

import io.civicpulse.identity.domain.Team;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, UUID> {

    List<Team> findAllByOrderByNameAsc();

    Optional<Team> findByName(String name);
}
