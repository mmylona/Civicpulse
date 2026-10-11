package io.civicpulse.reports.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.civicpulse.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@ActiveProfiles("demo")
@TestPropertySource(properties = "civicpulse.demo.reports=50")
class DemoReportsIT {

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private DemoReports demoReports;

    @Test
    void createsReportsOnce() throws Exception {
        assertThat(seeded()).isEqualTo(50);

        demoReports.run(null);

        assertThat(seeded()).isEqualTo(50);
        long withDistrict = jdbc.sql("""
                        SELECT count(*) FROM reports r JOIN users u ON u.id = r.reporter_id
                        WHERE u.email = 'residents@demo.local' AND r.district_code IS NOT NULL
                        """)
                .query(Long.class)
                .single();
        assertThat(withDistrict).isGreaterThan(40);
    }

    private long seeded() {
        return jdbc.sql("""
                        SELECT count(*) FROM reports r JOIN users u ON u.id = r.reporter_id
                        WHERE u.email = 'residents@demo.local'
                        """)
                .query(Long.class)
                .single();
    }
}
