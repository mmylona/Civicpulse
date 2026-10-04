package io.civicpulse.audit;

import static io.civicpulse.support.AuthTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.civicpulse.support.AuthTestClient;
import io.civicpulse.support.AuthTestClient.Session;
import io.civicpulse.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class AuditLogIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void registrationIsAudited() throws Exception {
        AuthTestClient auth = new AuthTestClient(mvc);
        String adminEmail = uniqueEmail();
        UUID adminId = auth.register(adminEmail).userId();
        jdbc.sql("INSERT INTO user_roles (user_id, role) VALUES (:id, 'ADMIN')").param("id", adminId).update();
        Session admin = auth.login(adminEmail);
        UUID citizenId = auth.register(uniqueEmail()).userId();

        mvc.perform(get("/api/v1/admin/audit")
                        .param("targetType", "USER")
                        .param("targetId", citizenId.toString())
                        .header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].action").value("USER_REGISTERED"))
                .andExpect(jsonPath("$.content[0].actorId").value(citizenId.toString()))
                .andExpect(jsonPath("$.content[0].details.email").isString())
                .andExpect(jsonPath("$.content[0].requestId").isString());
    }

    @Test
    void auditEntriesAreAppendOnly() {
        jdbc.sql("""
                        INSERT INTO audit_entries (id, occurred_at, action, target_type, target_id)
                        VALUES (:id, now(), 'TEST', 'TEST', 'x')
                        """)
                .param("id", UUID.randomUUID())
                .update();

        assertThatThrownBy(() -> jdbc.sql("UPDATE audit_entries SET action = 'CHANGED' WHERE action = 'TEST'").update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM audit_entries WHERE action = 'TEST'").update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }
}
