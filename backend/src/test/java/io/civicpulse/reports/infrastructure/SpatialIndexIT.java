package io.civicpulse.reports.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.civicpulse.support.IntegrationTest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

// Loads 100k reports and checks that the real map and nearby queries use the spatial indexes.
// Everything runs in one transaction that is rolled back, so other tests don't see the rows.
@IntegrationTest
@Transactional
class SpatialIndexIT {

    private static final int REPORTS = 100_000;

    // a few hundred metres around the Zytglogge
    private static final Map<String, Object> SMALL_BOX = Map.of(
            "west", 7.444, "south", 46.946, "east", 7.452, "north", 46.950);

    @Autowired
    private JdbcClient jdbc;

    // one test, so the rows are loaded only once
    @Test
    void mapAndNearbyQueriesUseTheSpatialIndexes() {
        loadReports();

        assertThat(explain(ReportGeoQueries.nearbySql(false),
                Map.of("latitude", 46.948, "longitude", 7.4474, "radius", 200.0, "limit", 20)))
                .as("nearby")
                .contains("reports_open_location_idx");
        assertThat(explain(ReportGeoQueries.markersSql(true, false),
                with(SMALL_BOX, "categoryCode", "POTHOLE", "limit", 501)))
                .as("markers")
                .contains("reports_open_location_idx");
        assertThat(explain(ReportGeoQueries.clustersSql(false, false),
                with(SMALL_BOX, "cell", 0.0055, "limit", 501)))
                .as("clusters")
                .contains("reports_open_location_idx");
        assertThat(explain(ReportGeoQueries.markersSql(false, true), with(SMALL_BOX, "limit", 501)))
                .as("markers with closed reports")
                .contains("reports_location_idx");
    }

    private void loadReports() {
        UUID reporter = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO users (id, email, password_hash, display_name, enabled, created_at)
                        VALUES (:id, :email, 'x', 'Load test', true, now())
                        """)
                .param("id", reporter)
                .param("email", "load-" + reporter + "@test.local")
                .update();
        // spread over the whole city, every fifth one closed
        jdbc.sql("""
                        INSERT INTO reports (id, reference, reporter_id, category_code, title, description,
                                             stated_severity, latitude, longitude, status, created_at, updated_at,
                                             resolved_at, closed_at)
                        SELECT gen_random_uuid(), 'LOAD-' || g, :reporter,
                               (ARRAY['POTHOLE', 'STREET_LIGHT', 'GRAFFITI', 'WATER_LEAK'])[1 + g % 4],
                               'Load test', 'Load test', (ARRAY['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'])[1 + g % 4],
                               46.925 + random() * 0.06, 7.295 + random() * 0.2,
                               CASE WHEN g % 5 = 0 THEN 'CLOSED' ELSE 'REPORTED' END,
                               now() - make_interval(mins => g), now(),
                               CASE WHEN g % 5 = 0 THEN now() END, CASE WHEN g % 5 = 0 THEN now() END
                        FROM generate_series(1, :count) g
                        """)
                .param("reporter", reporter)
                .param("count", REPORTS)
                .update();
        jdbc.sql("ANALYZE reports").update();
    }

    private String explain(String sql, Map<String, ?> params) {
        return String.join("\n", jdbc.sql("EXPLAIN " + sql).params(params).query(String.class).list());
    }

    private static Map<String, Object> with(Map<String, Object> base, Object... more) {
        Map<String, Object> params = new HashMap<>(base);
        for (int i = 0; i < more.length; i += 2) {
            params.put((String) more[i], more[i + 1]);
        }
        return params;
    }
}
