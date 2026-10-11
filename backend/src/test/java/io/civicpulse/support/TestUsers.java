package io.civicpulse.support;

import static io.civicpulse.support.AuthTestClient.uniqueEmail;

import io.civicpulse.shared.Ids;
import io.civicpulse.shared.security.Role;
import io.civicpulse.support.AuthTestClient.Session;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

// sets roles directly in the database before signing in
public final class TestUsers {

    private final AuthTestClient auth;
    private final JdbcClient jdbc;

    public TestUsers(MockMvc mvc, JdbcClient jdbc) {
        this.auth = new AuthTestClient(mvc);
        this.jdbc = jdbc;
    }

    public Session citizen() throws Exception {
        return auth.register(uniqueEmail());
    }

    public Session moderator() throws Exception {
        return withRole(Role.MODERATOR, null);
    }

    public Session operator(UUID teamId) throws Exception {
        return withRole(Role.CITY_OPERATOR, teamId);
    }

    public Session admin() throws Exception {
        return withRole(Role.ADMIN, null);
    }

    public Session withRole(Role role, UUID teamId) throws Exception {
        String email = uniqueEmail();
        UUID id = auth.register(email).userId();
        jdbc.sql("DELETE FROM user_roles WHERE user_id = :id").param("id", id).update();
        jdbc.sql("INSERT INTO user_roles (user_id, role) VALUES (:id, :role)")
                .param("id", id)
                .param("role", role.name())
                .update();
        if (teamId != null) {
            jdbc.sql("UPDATE users SET team_id = :teamId WHERE id = :id")
                    .param("teamId", teamId)
                    .param("id", id)
                    .update();
        }
        return auth.login(email);
    }

    public UUID team() {
        UUID id = Ids.newId();
        jdbc.sql("INSERT INTO teams (id, name, created_at) VALUES (:id, :name, :now)")
                .param("id", id)
                .param("name", "Team " + id)
                .param("now", OffsetDateTime.now(ZoneOffset.UTC))
                .update();
        return id;
    }
}
