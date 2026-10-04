package io.civicpulse.identity;

import static io.civicpulse.support.AuthTestClient.json;
import static io.civicpulse.support.AuthTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.civicpulse.support.AuthTestClient;
import io.civicpulse.support.AuthTestClient.Session;
import io.civicpulse.support.IntegrationTest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class AdminUsersIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    private AuthTestClient auth;
    private String adminEmail;
    private Session admin;
    private String citizenEmail;
    private Session citizen;

    @BeforeEach
    void setUp() throws Exception {
        auth = new AuthTestClient(mvc);
        adminEmail = uniqueEmail();
        UUID adminId = auth.register(adminEmail).userId();
        jdbc.sql("INSERT INTO user_roles (user_id, role) VALUES (:id, 'ADMIN')").param("id", adminId).update();
        admin = auth.login(adminEmail);

        citizenEmail = uniqueEmail();
        citizen = auth.register(citizenEmail);
    }

    @Test
    void listUsersRequiresAdmin() throws Exception {
        mvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(get("/api/v1/admin/users").header(HttpHeaders.AUTHORIZATION, citizen.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(get("/api/v1/admin/audit").header(HttpHeaders.AUTHORIZATION, citizen.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void searchByEmailAndRole() throws Exception {
        mvc.perform(get("/api/v1/admin/users").param("q", citizenEmail.toUpperCase())
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value(citizenEmail))
                .andExpect(jsonPath("$.content[0].enabled").value(true))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());

        mvc.perform(get("/api/v1/admin/users").param("q", citizenEmail).param("role", "ADMIN")
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void searchEscapesWildcards() throws Exception {
        mvc.perform(get("/api/v1/admin/users").param("q", "%").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void changeRolesRevokesSessionsAndIsAudited() throws Exception {
        mvc.perform(json(put("/api/v1/admin/users/{id}/roles", citizen.userId()), """
                        {"roles": ["MODERATOR", "CITIZEN"]}
                        """).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").value(List.of("CITIZEN", "MODERATOR")));

        mvc.perform(post("/api/v1/auth/refresh").cookie(citizen.refreshCookie()))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, auth.login(citizenEmail).bearer()))
                .andExpect(jsonPath("$.roles").value(List.of("CITIZEN", "MODERATOR")));

        AuditRow row = jdbc.sql("""
                        SELECT actor_id, details->>'newRoles' AS new_roles FROM audit_entries
                        WHERE target_id = :id AND action = 'USER_ROLES_CHANGED'
                        """)
                .param("id", citizen.userId().toString())
                .query((rs, n) -> new AuditRow(rs.getObject("actor_id", UUID.class), rs.getString("new_roles")))
                .single();
        assertThat(row.actorId()).isEqualTo(admin.userId());
        assertThat(row.newRoles()).isEqualTo("[\"CITIZEN\", \"MODERATOR\"]");
    }

    @Test
    void adminCannotLockThemselvesOut() throws Exception {
        mvc.perform(json(put("/api/v1/admin/users/{id}/roles", admin.userId()), """
                        {"roles": ["CITIZEN"]}
                        """).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_REMOVE_OWN_ADMIN_ROLE"));

        mvc.perform(json(put("/api/v1/admin/users/{id}/status", admin.userId()), """
                        {"enabled": false}
                        """).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_DISABLE_OWN_ACCOUNT"));
    }

    @Test
    void disabledUserCannotSignIn() throws Exception {
        mvc.perform(json(put("/api/v1/admin/users/{id}/status", citizen.userId()), """
                        {"enabled": false}
                        """).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mvc.perform(json(post("/api/v1/auth/login"),
                        "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(citizenEmail, AuthTestClient.PASSWORD))
                        .with(AuthTestClient.fromNewAddress()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").cookie(citizen.refreshCookie()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void assignTeam() throws Exception {
        UUID teamId = UUID.randomUUID();
        String teamName = "Team " + teamId;
        jdbc.sql("INSERT INTO teams (id, name, created_at) VALUES (:id, :name, :now)")
                .param("id", teamId)
                .param("name", teamName)
                .param("now", OffsetDateTime.now())
                .update();

        mvc.perform(json(put("/api/v1/admin/users/{id}/team", citizen.userId()), """
                        {"teamId": "%s"}
                        """.formatted(UUID.randomUUID())).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNKNOWN_TEAM"));

        mvc.perform(json(put("/api/v1/admin/users/{id}/team", citizen.userId()), """
                        {"teamId": "%s"}
                        """.formatted(teamId)).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.team.name").value(teamName));
    }

    @Test
    void invalidRoleChanges() throws Exception {
        mvc.perform(json(put("/api/v1/admin/users/{id}/roles", citizen.userId()), """
                        {"roles": []}
                        """).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mvc.perform(json(put("/api/v1/admin/users/{id}/roles", citizen.userId()), """
                        {"roles": ["SUPERUSER"]}
                        """).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isBadRequest());

        mvc.perform(json(put("/api/v1/admin/users/{id}/roles", UUID.randomUUID()), """
                        {"roles": ["CITIZEN"]}
                        """).header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    private record AuditRow(UUID actorId, String newRoles) {
    }
}
