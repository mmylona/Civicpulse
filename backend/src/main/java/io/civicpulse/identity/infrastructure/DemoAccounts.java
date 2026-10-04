package io.civicpulse.identity.infrastructure;

import io.civicpulse.identity.domain.Team;
import io.civicpulse.identity.domain.User;
import io.civicpulse.shared.Ids;
import io.civicpulse.shared.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("demo")
class DemoAccounts implements ApplicationRunner {

    static final String PASSWORD = "civicpulse-demo";

    private static final Logger log = LoggerFactory.getLogger(DemoAccounts.class);

    private static final List<String> TEAMS = List.of(
            "Roads and Infrastructure",
            "Street Lighting",
            "Waste Management",
            "Water and Drainage",
            "Traffic",
            "Parks and Public Space");

    private final UserRepository users;
    private final TeamRepository teams;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    DemoAccounts(UserRepository users, TeamRepository teams, PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.teams = teams;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Instant now = clock.instant();
        for (String name : TEAMS) {
            if (teams.findByName(name).isEmpty()) {
                teams.save(new Team(Ids.newId(), name, now));
            }
        }
        Team roads = teams.findByName("Roads and Infrastructure").orElseThrow();

        createIfMissing("citizen@demo.local", "Demo Citizen", Set.of(Role.CITIZEN), null, now);
        createIfMissing("moderator@demo.local", "Demo Moderator", Set.of(Role.MODERATOR), null, now);
        createIfMissing("operator@demo.local", "Demo Operator", Set.of(Role.CITY_OPERATOR), roads, now);
        createIfMissing("admin@demo.local", "Demo Admin", Set.of(Role.ADMIN), null, now);
    }

    private void createIfMissing(String email, String name, Set<Role> roles, Team team, Instant now) {
        if (users.existsByEmail(email)) {
            return;
        }
        User user = User.create(Ids.newId(), email, passwordEncoder.encode(PASSWORD), name, roles, now);
        user.assignTeam(team);
        users.save(user);
        log.info("Created demo account {}", email);
    }
}
