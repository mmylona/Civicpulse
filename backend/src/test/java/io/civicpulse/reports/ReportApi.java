package io.civicpulse.reports;

import static io.civicpulse.support.AuthTestClient.json;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.civicpulse.support.AuthTestClient.Session;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

final class ReportApi {

    static final String NEW_REPORT = """
            {"categoryCode": "POTHOLE", "title": "Deep pothole", "description": "Next to the tram stop, about 10 cm deep",
             "severity": "HIGH", "latitude": 46.9480, "longitude": 7.4474, "address": "Bubenbergplatz"}
            """;

    private final MockMvc mvc;

    ReportApi(MockMvc mvc) {
        this.mvc = mvc;
    }

    String create(Session reporter) throws Exception {
        String body = mvc.perform(json(post("/api/v1/reports"), NEW_REPORT)
                        .header(HttpHeaders.AUTHORIZATION, reporter.bearer()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    String createAt(Session reporter, double latitude, double longitude, String categoryCode) throws Exception {
        String body = mvc.perform(json(post("/api/v1/reports"), """
                        {"categoryCode": "%s", "title": "Test report", "description": "Placed by a test",
                         "severity": "MEDIUM", "latitude": %s, "longitude": %s}
                        """.formatted(categoryCode, latitude, longitude))
                        .header(HttpHeaders.AUTHORIZATION, reporter.bearer()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    String etag(String id) throws Exception {
        return mvc.perform(get("/api/v1/reports/{id}", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
    }

    // sends the current ETag as If-Match
    ResultActions act(String id, String action, String body, Session session) throws Exception {
        return mvc.perform(json(post("/api/v1/reports/{id}/{action}", id, action), body)
                .header(HttpHeaders.IF_MATCH, etag(id))
                .header(HttpHeaders.AUTHORIZATION, session.bearer()));
    }

    void ok(String id, String action, String body, Session session) throws Exception {
        act(id, action, body, session).andExpect(status().isOk());
    }

    String triaged(Session reporter, Session moderator) throws Exception {
        String id = create(reporter);
        ok(id, "triage", "{\"categoryCode\": \"POTHOLE\", \"severity\": \"HIGH\"}", moderator);
        return id;
    }

    String assigned(Session reporter, Session moderator, Session operator, UUID teamId) throws Exception {
        String id = triaged(reporter, moderator);
        ok(id, "assign", "{\"teamId\": \"%s\"}".formatted(teamId), operator);
        return id;
    }

    String inProgress(Session reporter, Session moderator, Session operator, UUID teamId) throws Exception {
        String id = assigned(reporter, moderator, operator, teamId);
        ok(id, "start", "{}", operator);
        return id;
    }

    String resolved(Session reporter, Session moderator, Session operator, UUID teamId) throws Exception {
        String id = inProgress(reporter, moderator, operator, teamId);
        ok(id, "resolve", "{\"resolution\": \"Filled with asphalt\"}", operator);
        return id;
    }
}
