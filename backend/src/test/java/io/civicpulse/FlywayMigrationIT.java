package io.civicpulse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.civicpulse.support.IntegrationTest;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class FlywayMigrationIT {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void allMigrationsApplyCleanlyToAnEmptyDatabase() {
        MigrationInfo[] applied = flyway.info().applied();

        assertThat(applied).isNotEmpty();
        assertThat(applied).allSatisfy(migration ->
                assertThat(migration.getState()).isEqualTo(MigrationState.SUCCESS));
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void requiredExtensionsAreInstalled() {
        List<String> extensions = jdbc.queryForList("SELECT extname FROM pg_extension", String.class);

        assertThat(extensions).contains("postgis", "pg_trgm", "citext");
    }

    @Test
    void identityAndAuditTablesExist() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);

        assertThat(tables).contains("users", "user_roles", "teams", "refresh_tokens", "audit_entries");
    }

    @Test
    void reportTablesExist() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);

        assertThat(tables).contains("categories", "reports", "report_events", "report_comments", "confirmations");
    }

    @Test
    void geographyDistanceIsMeasuredInMetres() {
        Double metres = jdbc.queryForObject("""
                SELECT ST_Distance(
                    ST_SetSRID(ST_MakePoint(7.4474, 46.9480), 4326)::geography,
                    ST_SetSRID(ST_MakePoint(7.4475, 46.9481), 4326)::geography)
                """, Double.class);

        // 0.0001° of latitude is about 11.1 m; 0.0001° of longitude at 47°N about 7.6 m.
        assertThat(metres).isCloseTo(13.5, within(1.0));
    }
}
