package io.civicpulse.reports;

import static io.civicpulse.support.AuthTestClient.fromNewAddress;
import static io.civicpulse.support.AuthTestClient.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.civicpulse.support.AuthTestClient.Session;
import io.civicpulse.support.IntegrationTest;
import io.civicpulse.support.TestUsers;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@IntegrationTest
class ReportMapIT {

    // about 67 m north
    private static final double NEAR = 0.0006;
    // about 550 m north
    private static final double FAR = 0.005;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    private ReportApi reports;
    private TestUsers users;
    private Session reporter;
    private double lat;
    private double lng;

    @BeforeEach
    void setUp() throws Exception {
        reports = new ReportApi(mvc);
        users = new TestUsers(mvc, jdbc);
        reporter = users.citizen();
        // a random spot in Bümpliz, so tests don't see each other's reports
        lat = ThreadLocalRandom.current().nextDouble(46.930, 46.955);
        lng = ThreadLocalRandom.current().nextDouble(7.300, 7.390);
    }

    @Test
    void nearbyIsOrderedByDistance() throws Exception {
        String near = reports.createAt(reporter, lat + NEAR, lng, "POTHOLE");
        String here = reports.createAt(reporter, lat, lng, "POTHOLE");
        String far = reports.createAt(reporter, lat + FAR, lng, "POTHOLE");

        String body = mvc.perform(nearby(lat, lng).param("radius", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(far))))
                .andReturn().getResponse().getContentAsString();

        List<String> ids = JsonPath.read(body, "$[*].id");
        assertThat(ids).contains(here, near);
        assertThat(ids.indexOf(here)).isLessThan(ids.indexOf(near));
        List<Integer> distances = JsonPath.read(body, "$[?(@.id == '" + near + "')].distanceMeters");
        assertThat(distances.get(0)).isBetween(60, 75);
    }

    @Test
    void nearbyByCategory() throws Exception {
        String pothole = reports.createAt(reporter, lat, lng, "POTHOLE");
        String graffiti = reports.createAt(reporter, lat, lng, "GRAFFITI");

        mvc.perform(nearby(lat, lng).param("category", "GRAFFITI"))
                .andExpect(jsonPath("$[*].id", hasItem(graffiti)))
                .andExpect(jsonPath("$[*].id", not(hasItem(pothole))));
    }

    @Test
    void nearbySkipsClosedReports() throws Exception {
        String id = reports.createAt(reporter, lat, lng, "POTHOLE");
        reports.ok(id, "reject", "{\"reason\": \"OTHER\"}", users.moderator());

        mvc.perform(nearby(lat, lng))
                .andExpect(jsonPath("$[*].id", not(hasItem(id))));
    }

    @Test
    void nearbyRadiusIsLimited() throws Exception {
        mvc.perform(nearby(lat, lng).param("radius", "5000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void markersWhenZoomedIn() throws Exception {
        String id = reports.createAt(reporter, lat, lng, "STREET_LIGHT");

        mvc.perform(map(box(0.001), 16))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("MARKERS"))
                .andExpect(jsonPath("$.clusters", empty()))
                .andExpect(jsonPath("$.markers[*].id", hasItem(id)))
                .andExpect(jsonPath("$.truncated").value(false));
    }

    @Test
    void clustersWhenZoomedOut() throws Exception {
        reports.createAt(reporter, lat, lng, "POTHOLE");
        reports.createAt(reporter, lat + NEAR, lng, "POTHOLE");

        String body = mvc.perform(map(box(0.05), 12))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("CLUSTERS"))
                .andExpect(jsonPath("$.markers", empty()))
                .andReturn().getResponse().getContentAsString();

        List<Integer> counts = JsonPath.read(body, "$.clusters[*].count");
        assertThat(counts.stream().mapToInt(Integer::intValue).sum()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void closedReportsOnlyWhenAsked() throws Exception {
        String id = reports.createAt(reporter, lat, lng, "POTHOLE");
        jdbc.sql("UPDATE reports SET status = 'CLOSED', closed_at = now() WHERE id = :id")
                .param("id", UUID.fromString(id))
                .update();

        mvc.perform(map(box(0.001), 16))
                .andExpect(jsonPath("$.markers[*].id", not(hasItem(id))));
        mvc.perform(map(box(0.001), 16).param("includeClosed", "true"))
                .andExpect(jsonPath("$.markers[*].id", hasItem(id)));
    }

    @Test
    void badBoundingBox() throws Exception {
        mvc.perform(map("7.3,46.9,7.4", 12))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BOUNDING_BOX"));
        // fine at zoom 12, far too large at zoom 18
        mvc.perform(map(box(0.05), 18))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BOUNDING_BOX"));
    }

    @Test
    void reportGetsItsDistrict() throws Exception {
        String id = reports.createAt(reporter, lat, lng, "POTHOLE");

        mvc.perform(get("/api/v1/reports/{id}", id))
                .andExpect(jsonPath("$.district.code").value("buempliz-oberbottigen"))
                .andExpect(jsonPath("$.district.name").value("Bümpliz-Oberbottigen"));
        mvc.perform(get("/api/v1/reports").param("district", "buempliz-oberbottigen").param("size", "100"))
                .andExpect(jsonPath("$.content[*].id", hasItem(id)));
        mvc.perform(get("/api/v1/reports").param("district", "innere-stadt").param("size", "100"))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(id))));
    }

    @Test
    void movingTheReportChangesTheDistrict() throws Exception {
        String id = reports.createAt(reporter, lat, lng, "POTHOLE");

        mvc.perform(json(patch("/api/v1/reports/{id}", id), "{\"latitude\": 46.9479, \"longitude\": 7.4478}")
                        .header(HttpHeaders.IF_MATCH, reports.etag(id))
                        .header(HttpHeaders.AUTHORIZATION, reporter.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.district.code").value("innere-stadt"));

        mvc.perform(json(patch("/api/v1/reports/{id}", id), "{\"latitude\": 47.3769, \"longitude\": 8.5417}")
                        .header(HttpHeaders.IF_MATCH, reports.etag(id))
                        .header(HttpHeaders.AUTHORIZATION, reporter.bearer()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("LOCATION_OUTSIDE_CITY"));
    }

    @Test
    void reportsOutsideTheCityAreRejected() throws Exception {
        mvc.perform(json(post("/api/v1/reports"), ReportApi.NEW_REPORT
                        .replace("46.9480", "47.3769")
                        .replace("7.4474", "8.5417"))
                        .header(HttpHeaders.AUTHORIZATION, reporter.bearer()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("LOCATION_OUTSIDE_CITY"));
    }

    @Test
    void staffSeeCriticalSitesNearby() throws Exception {
        // next to the main station
        String id = reports.createAt(reporter, 46.9492, 7.4393, "STREET_LIGHT");

        mvc.perform(get("/api/v1/reports/{id}", id).header(HttpHeaders.AUTHORIZATION, users.moderator().bearer()))
                .andExpect(jsonPath("$.staff.criticalSitesNearby[*].name", hasItem("Bern station")));
    }

    private MockHttpServletRequestBuilder nearby(double latitude, double longitude) {
        return get("/api/v1/reports/nearby")
                .param("lat", String.valueOf(latitude))
                .param("lng", String.valueOf(longitude))
                .with(fromNewAddress());
    }

    private MockHttpServletRequestBuilder map(String bbox, int zoom) {
        return get("/api/v1/reports/map")
                .param("bbox", bbox)
                .param("zoom", String.valueOf(zoom))
                .with(fromNewAddress());
    }

    private String box(double half) {
        return String.format(Locale.ROOT, "%.6f,%.6f,%.6f,%.6f", lng - half, lat - half, lng + half, lat + half);
    }
}
