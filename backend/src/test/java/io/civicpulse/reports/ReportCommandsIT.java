package io.civicpulse.reports;

import static io.civicpulse.support.AuthTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.civicpulse.reports.application.ReportAutoClose;
import io.civicpulse.support.AuthTestClient.Session;
import io.civicpulse.support.IntegrationTest;
import io.civicpulse.support.TestUsers;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class ReportCommandsIT {

    private static final String TRIAGE = "{\"categoryCode\": \"POTHOLE\", \"severity\": \"HIGH\"}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private ReportAutoClose autoClose;

    private TestUsers users;
    private ReportApi reports;
    private Session reporter;
    private Session moderator;
    private UUID roads;
    private Session roadsOperator;
    private Session otherOperator;

    @BeforeEach
    void setUp() throws Exception {
        users = new TestUsers(mvc, jdbc);
        reports = new ReportApi(mvc);
        reporter = users.citizen();
        moderator = users.moderator();
        roads = users.team();
        roadsOperator = users.operator(roads);
        otherOperator = users.operator(users.team());
    }

    @Test
    void triage() throws Exception {
        String id = reports.create(reporter);

        reports.act(id, "triage", TRIAGE, reporter)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        reports.act(id, "triage", "{\"categoryCode\": \"STREET_LIGHT\", \"severity\": \"LOW\"}", moderator)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(jsonPath("$.status").value("TRIAGED"))
                .andExpect(jsonPath("$.categoryCode").value("STREET_LIGHT"))
                .andExpect(jsonPath("$.severity").value("LOW"))
                .andExpect(jsonPath("$.staff.statedSeverity").value("HIGH"));
    }

    @Test
    void reject() throws Exception {
        String id = reports.create(reporter);
        String body = "{\"reason\": \"NOT_A_CITY_MATTER\", \"note\": \"Private parking lot\"}";

        reports.act(id, "reject", body, roadsOperator).andExpect(status().isForbidden());
        reports.act(id, "reject", body, moderator)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mvc.perform(get("/api/v1/reports/{id}/timeline", id))
                .andExpect(jsonPath("$[1].details.reason").value("NOT_A_CITY_MATTER"))
                .andExpect(jsonPath("$[1].details.message").value("Private parking lot"));
    }

    @Test
    void markDuplicate() throws Exception {
        String original = reports.create(users.citizen());
        String first = reports.create(reporter);
        String second = reports.create(reporter);

        reports.act(first, "mark-duplicate", "{\"duplicateOf\": \"%s\"}".formatted(second), moderator)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DUPLICATE"))
                .andExpect(jsonPath("$.duplicateOf.id").value(second));

        // second becomes a duplicate too; first now points at the original
        reports.ok(second, "mark-duplicate", "{\"duplicateOf\": \"%s\"}".formatted(original), roadsOperator);
        mvc.perform(get("/api/v1/reports/{id}", first))
                .andExpect(jsonPath("$.duplicateOf.id").value(original));

        // marking against a duplicate goes to the end of the chain
        String third = reports.create(reporter);
        reports.act(third, "mark-duplicate", "{\"duplicateOf\": \"%s\"}".formatted(first), moderator)
                .andExpect(jsonPath("$.duplicateOf.id").value(original));
    }

    @Test
    void duplicateOfClosedReportIsRejected() throws Exception {
        String rejected = reports.create(reporter);
        reports.ok(rejected, "reject", "{\"reason\": \"OTHER\"}", moderator);
        String id = reports.create(reporter);

        reports.act(id, "mark-duplicate", "{\"duplicateOf\": \"%s\"}".formatted(rejected), moderator)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_TARGET_INVALID"));
        reports.act(id, "mark-duplicate", "{\"duplicateOf\": \"%s\"}".formatted(id), moderator)
                .andExpect(status().isConflict());
    }

    @Test
    void requestAndProvideInformation() throws Exception {
        String id = reports.triaged(reporter, moderator);

        reports.act(id, "request-information", "{\"question\": \"Which side of the street?\"}", moderator)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING_FOR_INFORMATION"))
                .andExpect(jsonPath("$.staff.resumeStatus").value("TRIAGED"));

        reports.act(id, "provide-information", "{\"answer\": \"North side\"}", users.citizen())
                .andExpect(status().isForbidden());
        reports.act(id, "provide-information", "{\"answer\": \"North side\"}", reporter)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRIAGED"));
    }

    @Test
    void resume() throws Exception {
        String id = reports.inProgress(reporter, moderator, roadsOperator, roads);
        reports.ok(id, "request-information", "{\"question\": \"Is it still blocking the path?\"}", roadsOperator);

        reports.act(id, "resume", "{\"note\": \"Checked on site\"}", roadsOperator)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void assign() throws Exception {
        String id = reports.triaged(reporter, moderator);
        Session lightingOperator = users.operator(users.team());

        reports.act(id, "assign", "{\"teamId\": \"%s\"}".formatted(UUID.randomUUID()), roadsOperator)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNKNOWN_TEAM"));
        reports.act(id, "assign", "{\"teamId\": \"%s\", \"assigneeId\": \"%s\"}"
                        .formatted(roads, lightingOperator.userId()), roadsOperator)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ASSIGNEE_NOT_IN_TEAM"));
        reports.act(id, "assign", "{\"teamId\": \"%s\"}".formatted(roads), moderator)
                .andExpect(status().isForbidden());

        reports.act(id, "assign", "{\"teamId\": \"%s\", \"assigneeId\": \"%s\"}"
                        .formatted(roads, roadsOperator.userId()), otherOperator)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.staff.team.id").value(roads.toString()))
                .andExpect(jsonPath("$.staff.assignee.id").value(roadsOperator.userId().toString()));
    }

    @Test
    void unassign() throws Exception {
        String id = reports.assigned(reporter, moderator, roadsOperator, roads);

        reports.act(id, "unassign", "{\"reason\": \"Wrong team, this is a tram track\"}", otherOperator)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRIAGED"))
                .andExpect(jsonPath("$.staff.team").value(nullValue()));
    }

    @Test
    void start() throws Exception {
        String id = reports.assigned(reporter, moderator, roadsOperator, roads);

        reports.act(id, "start", "{}", otherOperator)
                .andExpect(status().isForbidden());
        reports.act(id, "start", "{}", roadsOperator)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.staff.assignee.id").value(roadsOperator.userId().toString()));
    }

    @Test
    void handBack() throws Exception {
        String id = reports.inProgress(reporter, moderator, roadsOperator, roads);

        reports.act(id, "hand-back", "{\"note\": \"Needs a bigger machine\"}", roadsOperator)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.staff.assignee").value(nullValue()));
    }

    @Test
    void resolve() throws Exception {
        String id = reports.inProgress(reporter, moderator, roadsOperator, roads);

        reports.act(id, "resolve", "{\"resolution\": \"Filled\"}", otherOperator)
                .andExpect(status().isForbidden());
        reports.act(id, "resolve", "{\"resolution\": \"Filled with asphalt\"}", roadsOperator)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());
    }

    @Test
    void reopen() throws Exception {
        String id = reports.resolved(reporter, moderator, roadsOperator, roads);

        reports.act(id, "reopen", "{\"comment\": \"Still there\"}", users.citizen())
                .andExpect(status().isForbidden());
        reports.act(id, "reopen", "{\"comment\": \"The hole is back after the rain\"}", reporter)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.resolvedAt").value(nullValue()));
    }

    @Test
    void reopenWindowCloses() throws Exception {
        String id = reports.resolved(reporter, moderator, roadsOperator, roads);
        jdbc.sql("UPDATE reports SET resolved_at = :at WHERE id = :id")
                .param("at", OffsetDateTime.now(ZoneOffset.UTC).minusDays(15))
                .param("id", UUID.fromString(id))
                .update();

        reports.act(id, "reopen", "{\"comment\": \"Still there\"}", reporter)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REOPEN_WINDOW_CLOSED"));
    }

    @Test
    void close() throws Exception {
        String id = reports.resolved(reporter, moderator, roadsOperator, roads);

        reports.act(id, "close", "{}", reporter).andExpect(status().isForbidden());
        reports.act(id, "close", "{}", otherOperator)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void autoCloseAfterReopenWindow() throws Exception {
        String id = reports.resolved(reporter, moderator, roadsOperator, roads);
        jdbc.sql("UPDATE reports SET resolved_at = :at WHERE id = :id")
                .param("at", OffsetDateTime.now(ZoneOffset.UTC).minusDays(15))
                .param("id", UUID.fromString(id))
                .update();

        autoClose.run();

        mvc.perform(get("/api/v1/reports/{id}", id))
                .andExpect(jsonPath("$.status").value("CLOSED"));
        mvc.perform(get("/api/v1/reports/{id}/timeline", id))
                .andExpect(jsonPath("$[-1:].actorKind", contains("SYSTEM")))
                .andExpect(jsonPath("$[-1:].details.to", contains("CLOSED")));
    }

    @Test
    void restoreIsAudited() throws Exception {
        String id = reports.create(reporter);
        reports.ok(id, "reject", "{\"reason\": \"INAPPROPRIATE\"}", moderator);

        reports.act(id, "restore", "{\"reason\": \"Rejected by mistake\"}", roadsOperator)
                .andExpect(status().isForbidden());
        reports.act(id, "restore", "{\"reason\": \"Rejected by mistake\"}", moderator)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRIAGED"))
                .andExpect(jsonPath("$.staff.validatedSeverity").value("HIGH"));

        String reason = jdbc.sql("""
                        SELECT details->>'reason' FROM audit_entries
                        WHERE action = 'REPORT_RESTORED' AND target_id = :id
                        """)
                .param("id", id)
                .query(String.class)
                .single();
        assertThat(reason).isEqualTo("Rejected by mistake");
    }

    @Test
    void wrongStatusIsConflict() throws Exception {
        String id = reports.create(reporter);

        reports.act(id, "start", "{}", roadsOperator)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void adminCannotWorkOnReports() throws Exception {
        String id = reports.create(reporter);

        reports.act(id, "triage", TRIAGE, users.admin())
                .andExpect(status().isForbidden());
    }

    @Test
    void ifMatchIsRequired() throws Exception {
        String id = reports.create(reporter);

        mvc.perform(json(post("/api/v1/reports/{id}/triage", id), TRIAGE)
                        .header(HttpHeaders.AUTHORIZATION, moderator.bearer()))
                .andExpect(status().isPreconditionRequired());
        mvc.perform(json(post("/api/v1/reports/{id}/triage", id), TRIAGE)
                        .header(HttpHeaders.IF_MATCH, "W/\"0\"")
                        .header(HttpHeaders.AUTHORIZATION, moderator.bearer()))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void staleVersionIsRejected() throws Exception {
        String id = reports.create(reporter);
        String etag = reports.etag(id);
        reports.ok(id, "triage", TRIAGE, moderator);

        // a second operator still has the old version open
        mvc.perform(json(post("/api/v1/reports/{id}/reject", id), "{\"reason\": \"OTHER\"}")
                        .header(HttpHeaders.IF_MATCH, etag)
                        .header(HttpHeaders.AUTHORIZATION, moderator.bearer()))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void missingNoteIsValidationError() throws Exception {
        String id = reports.inProgress(reporter, moderator, roadsOperator, roads);

        reports.act(id, "resolve", "{\"resolution\": \"  \"}", roadsOperator)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("resolution"));
    }

    @Test
    void availableActions() throws Exception {
        String id = reports.create(reporter);

        mvc.perform(get("/api/v1/reports/{id}/actions", id).header(HttpHeaders.AUTHORIZATION, moderator.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actions", contains("TRIAGE", "REJECT", "MARK_DUPLICATE", "REQUEST_INFORMATION")))
                .andExpect(jsonPath("$.canEdit").value(false))
                .andExpect(jsonPath("$.canModerateComments").value(true));

        mvc.perform(get("/api/v1/reports/{id}/actions", id).header(HttpHeaders.AUTHORIZATION, reporter.bearer()))
                .andExpect(jsonPath("$.actions", empty()))
                .andExpect(jsonPath("$.canEdit").value(true))
                .andExpect(jsonPath("$.canConfirm").value(false));

        mvc.perform(get("/api/v1/reports/{id}/actions", id).header(HttpHeaders.AUTHORIZATION, users.citizen().bearer()))
                .andExpect(jsonPath("$.canConfirm").value(true))
                .andExpect(jsonPath("$.canComment").value(true));

        mvc.perform(get("/api/v1/reports/{id}/actions", id))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicTimelineShowsStatusChanges() throws Exception {
        String id = reports.resolved(reporter, moderator, roadsOperator, roads);

        mvc.perform(get("/api/v1/reports/{id}/timeline", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].details.to", contains("TRIAGED", "ASSIGNED", "IN_PROGRESS", "RESOLVED")))
                .andExpect(jsonPath("$[0].type").value("SUBMITTED"))
                .andExpect(jsonPath("$[0].actorKind").value("REPORTER"))
                .andExpect(jsonPath("$[*].actorName", everyItem(nullValue())));
    }
}
