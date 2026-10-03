package io.civicpulse.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A real PostgreSQL + PostGIS database for integration tests (never H2; see ADR-002).
 *
 * <p>Declared as a bean so Spring starts it once per cached application context and wires
 * the datasource through {@link ServiceConnection}. The image tag comes from the
 * {@code postgis.image} Maven property so tests and docker-compose use the same version.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    static final String POSTGIS_IMAGE =
            System.getProperty("civicpulse.test.postgis-image", "postgis/postgis:17-3.5");

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgisContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse(POSTGIS_IMAGE).asCompatibleSubstituteFor("postgres"));
    }
}
