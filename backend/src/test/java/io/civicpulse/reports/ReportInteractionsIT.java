package io.civicpulse.reports;

import static io.civicpulse.support.AuthTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.civicpulse.support.AuthTestClient.Session;
import io.civicpulse.support.IntegrationTest;
import io.civicpulse.support.TestUsers;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@IntegrationTest
class ReportInteractionsIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    private TestUsers users;
    private ReportApi reports;
    private Session reporter;
    private Session neighbour;
    private Session moderator;

    @BeforeEach
    void setUp() throws Exception {
        users = new TestUsers(mvc, jdbc);
        reports = new ReportApi(mvc);
        reporter = users.citizen();
        neighbour = users.citizen();
        moderator = users.moderator();
    }

    @Test
    void citizensComment() throws Exception {
        String id = reports.create(reporter);

        mvc.perform(json(post("/api/v1/reports/{id}/comments", id), "{\"body\": \"Saw it this morning too\"}")
                        .header(HttpHeaders.AUTHORIZATION, neighbour.bearer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorKind").value("CITIZEN"))
                .andExpect(jsonPath("$.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.mine").value(true));

        mvc.perform(get("/api/v1/reports/{id}/comments", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].body").value("Saw it this morning too"))
                .andExpect(jsonPath("$[0].authorName").value(nullValue()));

        mvc.perform(json(post("/api/v1/reports/{id}/comments", id), "{\"body\": \"Hi\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyStaffWriteInternalComments() throws Exception {
        String id = reports.create(reporter);
        String internal = "{\"body\": \"Same spot as last year\", \"visibility\": \"INTERNAL\"}";

        mvc.perform(json(post("/api/v1/reports/{id}/comments", id), internal)
                        .header(HttpHeaders.AUTHORIZATION, reporter.bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(json(post("/api/v1/reports/{id}/comments", id), internal)
                        .header(HttpHeaders.AUTHORIZATION, moderator.bearer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorKind").value("STAFF"));

        mvc.perform(get("/api/v1/reports/{id}/comments", id).header(HttpHeaders.AUTHORIZATION, reporter.bearer()))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/v1/reports/{id}/comments", id).header(HttpHeaders.AUTHORIZATION, moderator.bearer()))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].authorName").value("Test User"));
    }

    @Test
    void moderatorHidesComment() throws Exception {
        String id = reports.create(reporter);
        String body = mvc.perform(json(post("/api/v1/reports/{id}/comments", id), "{\"body\": \"Rude words\"}")
                        .header(HttpHeaders.AUTHORIZATION, neighbour.bearer()))
                .andReturn().getResponse().getContentAsString();
        String commentId = JsonPath.read(body, "$.id");
        String hide = "{\"hidden\": true, \"reason\": \"Offensive\"}";

        mvc.perform(json(patch("/api/v1/reports/{id}/comments/{cid}", id, commentId), hide)
                        .header(HttpHeaders.AUTHORIZATION, users.operator(users.team()).bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(json(patch("/api/v1/reports/{id}/comments/{cid}", id, commentId), hide)
                        .header(HttpHeaders.AUTHORIZATION, moderator.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hidden").value(true))
                .andExpect(jsonPath("$.body").value("Rude words"))
                .andExpect(jsonPath("$.hiddenReason").value("Offensive"));

        mvc.perform(get("/api/v1/reports/{id}/comments", id))
                .andExpect(jsonPath("$[0].hidden").value(true))
                .andExpect(jsonPath("$[0].body").value(nullValue()))
                .andExpect(jsonPath("$[0].hiddenReason").value(nullValue()));

        int audited = jdbc.sql("SELECT count(*) FROM audit_entries WHERE action = 'COMMENT_HIDDEN' AND target_id = :id")
                .param("id", commentId)
                .query(Integer.class)
                .single();
        assertThat(audited).isEqualTo(1);
    }

    @Test
    void noCitizenCommentsOnClosedReports() throws Exception {
        String id = reports.create(reporter);
        reports.ok(id, "reject", "{\"reason\": \"OTHER\"}", moderator);

        mvc.perform(json(post("/api/v1/reports/{id}/comments", id), "{\"body\": \"Why?\"}")
                        .header(HttpHeaders.AUTHORIZATION, reporter.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_CLOSED"));
    }

    @Test
    void confirmations() throws Exception {
        String id = reports.create(reporter);
        String path = "/api/v1/reports/{id}/confirmation";

        mvc.perform(confirm(path, id, "STILL_PRESENT", neighbour))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myConfirmation").value("STILL_PRESENT"))
                .andExpect(jsonPath("$.stillPresentCount").value(1));
        // same request again changes nothing
        mvc.perform(confirm(path, id, "STILL_PRESENT", neighbour))
                .andExpect(jsonPath("$.stillPresentCount").value(1));
        mvc.perform(confirm(path, id, "FIXED", neighbour))
                .andExpect(jsonPath("$.stillPresentCount").value(0))
                .andExpect(jsonPath("$.fixedCount").value(1));

        mvc.perform(get("/api/v1/reports/{id}", id).header(HttpHeaders.AUTHORIZATION, neighbour.bearer()))
                .andExpect(jsonPath("$.myConfirmation").value("FIXED"))
                .andExpect(jsonPath("$.fixedCount").value(1));

        mvc.perform(delete(path, id).header(HttpHeaders.AUTHORIZATION, neighbour.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myConfirmation").value(nullValue()))
                .andExpect(jsonPath("$.fixedCount").value(0));
    }

    @Test
    void confirmationRules() throws Exception {
        String id = reports.create(reporter);
        String path = "/api/v1/reports/{id}/confirmation";

        mvc.perform(confirm(path, id, "STILL_PRESENT", reporter))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OWN_REPORT"));
        mvc.perform(confirm(path, id, "STILL_PRESENT", moderator))
                .andExpect(status().isForbidden());
        mvc.perform(confirm(path, UUID.randomUUID().toString(), "STILL_PRESENT", neighbour))
                .andExpect(status().isNotFound());

        reports.ok(id, "reject", "{\"reason\": \"OTHER\"}", moderator);
        mvc.perform(confirm(path, id, "STILL_PRESENT", neighbour))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_CLOSED"));
    }

    @Test
    void stillPresentAfterResolveIsFlaggedForStaff() throws Exception {
        UUID team = users.team();
        Session operator = users.operator(team);
        String id = reports.resolved(reporter, moderator, operator, team);
        String path = "/api/v1/reports/{id}/confirmation";

        mvc.perform(confirm(path, id, "STILL_PRESENT", neighbour)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/reports/{id}", id).header(HttpHeaders.AUTHORIZATION, operator.bearer()))
                .andExpect(jsonPath("$.staff.reopenRequested").value(false));

        mvc.perform(confirm(path, id, "STILL_PRESENT", users.citizen())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/reports/{id}", id).header(HttpHeaders.AUTHORIZATION, operator.bearer()))
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.staff.reopenRequested").value(true));
    }

    // a staff-only text must never show up in anything a citizen or visitor can load
    @Test
    void internalTextDoesNotLeak() throws Exception {
        String secret = "internal-" + UUID.randomUUID();
        UUID team = users.team();
        Session operator = users.operator(team);
        String id = reports.inProgress(reporter, moderator, operator, team);
        reports.ok(id, "hand-back", "{\"note\": \"%s\"}".formatted(secret), operator);
        mvc.perform(json(post("/api/v1/reports/{id}/comments", id),
                        "{\"body\": \"%s\", \"visibility\": \"INTERNAL\"}".formatted(secret))
                        .header(HttpHeaders.AUTHORIZATION, operator.bearer()))
                .andExpect(status().isCreated());

        String[] urls = {"/api/v1/reports/" + id, "/api/v1/reports/" + id + "/timeline",
                "/api/v1/reports/" + id + "/comments", "/api/v1/reports?size=100"};
        for (Session citizen : new Session[] {null, reporter, neighbour}) {
            for (String url : urls) {
                MockHttpServletRequestBuilder request = get(url);
                if (citizen != null) {
                    request.header(HttpHeaders.AUTHORIZATION, citizen.bearer());
                }
                String content = mvc.perform(request).andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString();
                assertThat(content).as(url)
                        .doesNotContain(secret)
                        .doesNotContain(operator.userId().toString())
                        .doesNotContain(team.toString());
            }
        }

        mvc.perform(get("/api/v1/reports/{id}/timeline", id).header(HttpHeaders.AUTHORIZATION, moderator.bearer()))
                .andExpect(jsonPath("$[*].details.note", hasItem(secret)));
    }

    @Test
    void staffViewEndsWithTheRole() throws Exception {
        String id = reports.create(reporter);
        mvc.perform(get("/api/v1/reports/{id}", id).header(HttpHeaders.AUTHORIZATION, moderator.bearer()))
                .andExpect(jsonPath("$.staff.reporter.id").value(reporter.userId().toString()));

        // the token still says MODERATOR, the database no longer does
        jdbc.sql("UPDATE user_roles SET role = 'CITIZEN' WHERE user_id = :id")
                .param("id", moderator.userId())
                .update();

        mvc.perform(get("/api/v1/reports/{id}", id).header(HttpHeaders.AUTHORIZATION, moderator.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staff").value(nullValue()));
        mvc.perform(get("/api/v1/reports/{id}/actions", id).header(HttpHeaders.AUTHORIZATION, moderator.bearer()))
                .andExpect(jsonPath("$.actions", hasSize(0)));
    }

    @Test
    void timelineIsAppendOnly() throws Exception {
        UUID id = UUID.fromString(reports.create(reporter));

        assertThatThrownBy(() -> jdbc.sql("UPDATE report_events SET type = 'NOTE' WHERE report_id = :id")
                        .param("id", id)
                        .update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM report_events WHERE report_id = :id").param("id", id).update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    private static MockHttpServletRequestBuilder confirm(String path, String id, String kind, Session session) {
        return json(put(path, id), "{\"kind\": \"%s\"}".formatted(kind))
                .header(HttpHeaders.AUTHORIZATION, session.bearer());
    }
}
