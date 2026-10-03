package io.civicpulse;

import io.civicpulse.support.TestcontainersConfiguration;
import org.springframework.boot.SpringApplication;

/**
 * Runs the backend locally against a throwaway PostGIS container, without docker-compose:
 * {@code mvn spring-boot:test-run}. Requires Docker.
 */
public class TestCivicPulseApplication {

    public static void main(String[] args) {
        SpringApplication.from(CivicPulseApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
