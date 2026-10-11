package io.civicpulse.reports;

import static io.civicpulse.reports.ReportApi.NEW_REPORT;
import static io.civicpulse.support.AuthTestClient.json;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.civicpulse.support.AuthTestClient.Session;
import io.civicpulse.support.IntegrationTest;
import io.civicpulse.support.TestUsers;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class ReportsIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    private TestUsers users;
    private ReportApi reports;
    private Session reporter;

    @BeforeEach
    void setUp() throws Exception {
        users = new TestUsers(mvc, jdbc);
        reports = new ReportApi(mvc);
        reporter = users.citizen();
    }

    @Test
    void createReport() throws Exception {
        mvc.perform(json(post("/api/v1/reports"), NEW_REPORT).header(HttpHeaders.AUTHORIZATION, reporter.bearer()))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern("/api/v1/reports/[0-9a-f-]{36}")))
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.reference").value(matchesPattern("CP-\\d{5,}")))
                .andExpect(jsonPath("$.status").value("REPORTED"))
                .andExpect(jsonPath("$.severity").value("HIGH"))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.staff").value(nullValue()));
    }

    @Test
    void createNeedsCitizenRole() throws Exception {
        mvc.perform(json(post("/api/v1/reports"), NEW_REPORT))
                .andExpect(status().isUnauthorized());
        mvc.perform(json(post("/api/v1/reports"), NEW_REPORT)
                        .header(HttpHeaders.AUTHORIZATION, users.moderator().bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void createValidatesInput() throws Exception {
        mvc.perform(json(post("/api/v1/reports"), """
                        {"categoryCode": "POTHOLE", "title": " ", "description": "x", "severity": "HIGH",
                         "latitude": 95, "longitude": 7.4}
                        """).header(HttpHeaders.AUTHORIZATION, reporter.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("title")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("latitude")));

        mvc.perform(json(post("/api/v1/reports"), NEW_REPORT.replace("POTHOLE", "VOLCANO"))
                        .header(HttpHeaders.AUTHORIZATION, reporter.bearer()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNKNOWN_CATEGORY"));
    }

    @Test
    void sameIdempotencyKeyReturnsSameReport() throws Exception {
        String key = UUID.randomUUID().toString();
        String first = mvc.perform(json(post("/api/v1/reports"), NEW_REPORT)
                        .header("Idempotency-Key", key)
                        .header(HttpHeaders.AUTHORIZATION, reporter.bearer()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

        mvc.perform(json(post("/api/v1/reports"), NEW_REPORT)
                        .header("Idempotency-Key", key)
                        .header(HttpHeaders.AUTHORIZATION, reporter.bearer()))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, first));

        mvc.perform(get("/api/v1/reports/mine").header(HttpHeaders.AUTHORIZATION, reporter.bearer()))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void publicViewHasNoStaffDetails() throws Exception {
        String id = reports.create(reporter);

        mvc.perform(get("/api/v1/reports/{id}", id))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.mine").value(false))
                .andExpect(jsonPath("$.staff").value(nullValue()));

        mvc.perform(get("/api/v1/reports/{id}", id).header(HttpHeaders.AUTHORIZATION, users.citizen().bearer()))
                .andExpect(jsonPath("$.mine").value(false))
                .andExpect(jsonPath("$.staff").value(nullValue()));

        mvc.perform(get("/api/v1/reports/{id}", id).header(HttpHeaders.AUTHORIZATION, users.moderator().bearer()))
                .andExpect(jsonPath("$.staff.reporter.id").value(reporter.userId().toString()))
                .andExpect(jsonPath("$.staff.reporter.displayName").value("Test User"))
                .andExpect(jsonPath("$.staff.statedSeverity").value("HIGH"));
    }

    @Test
    void unknownReportIs404() throws Exception {
        mvc.perform(get("/api/v1/reports/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
    }

    @Test
    void searchAndMine() throws Exception {
        String id = reports.create(reporter);
        Session other = users.citizen();
        reports.create(other);

        mvc.perform(get("/api/v1/reports").param("q", "TRAM STOP").param("status", "REPORTED").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem(id)));

        mvc.perform(get("/api/v1/reports").param("status", "CLOSED").param("size", "100"))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(id))));

        mvc.perform(get("/api/v1/reports/mine").header(HttpHeaders.AUTHORIZATION, reporter.bearer()))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id));

        mvc.perform(get("/api/v1/reports/mine"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void editNeedsIfMatch() throws Exception {
        String id = reports.create(reporter);
        String body = """
                {"title": "Very deep pothole", "severity": "CRITICAL"}
                """;

        mvc.perform(json(patch("/api/v1/reports/{id}", id), body).header(HttpHeaders.AUTHORIZATION, reporter.bearer()))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("PRECONDITION_REQUIRED"));

        mvc.perform(json(patch("/api/v1/reports/{id}", id), body)
                        .header(HttpHeaders.IF_MATCH, "\"7\"")
                        .header(HttpHeaders.AUTHORIZATION, reporter.bearer()))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        mvc.perform(json(patch("/api/v1/reports/{id}", id), body)
                        .header(HttpHeaders.IF_MATCH, "\"0\"")
                        .header(HttpHeaders.AUTHORIZATION, reporter.bearer()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(jsonPath("$.title").value("Very deep pothole"))
                .andExpect(jsonPath("$.severity").value("CRITICAL"))
                .andExpect(jsonPath("$.description").value("Next to the tram stop, about 10 cm deep"));

        mvc.perform(get("/api/v1/reports/{id}/timeline", id))
                .andExpect(jsonPath("$[1].type").value("EDITED"))
                .andExpect(jsonPath("$[1].details.fields", hasItem("title")));
    }

    @Test
    void onlyReporterEditsAndOnlyBeforeTriage() throws Exception {
        String id = reports.create(reporter);
        String body = "{\"title\": \"Something else\"}";

        mvc.perform(json(patch("/api/v1/reports/{id}", id), body)
                        .header(HttpHeaders.IF_MATCH, reports.etag(id))
                        .header(HttpHeaders.AUTHORIZATION, users.citizen().bearer()))
                .andExpect(status().isForbidden());

        reports.ok(id, "triage", "{\"categoryCode\": \"POTHOLE\", \"severity\": \"LOW\"}", users.moderator());

        mvc.perform(json(patch("/api/v1/reports/{id}", id), body)
                        .header(HttpHeaders.IF_MATCH, reports.etag(id))
                        .header(HttpHeaders.AUTHORIZATION, reporter.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_EDITABLE"));
    }

    @Test
    void categoriesArePublic() throws Exception {
        mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("POTHOLE"))
                .andExpect(jsonPath("$[*].code", hasItem("STREET_LIGHT")));
    }
}
