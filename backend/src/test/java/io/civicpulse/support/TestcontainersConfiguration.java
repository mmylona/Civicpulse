package io.civicpulse.support;

import java.time.Duration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    static final String POSTGIS_IMAGE =
            System.getProperty("civicpulse.test.postgis-image", "postgis/postgis:17-3.5");

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgisContainer() {
        // amd64-only image: under emulation (Apple Silicon) the first start can exceed 60 s
        return new PostgreSQLContainer<>(DockerImageName.parse(POSTGIS_IMAGE).asCompatibleSubstituteFor("postgres"))
                .withStartupTimeout(Duration.ofMinutes(3));
    }
}
