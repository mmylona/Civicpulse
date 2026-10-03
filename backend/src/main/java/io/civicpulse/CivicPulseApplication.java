package io.civicpulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * CivicPulse backend: a modular monolith (see docs/adr/001-modular-monolith.md).
 *
 * <p>Each direct sub-package of {@code io.civicpulse} is an application module whose
 * boundaries are verified by {@code ModularityTests}.
 */
@SpringBootApplication
public class CivicPulseApplication {

    public static void main(String[] args) {
        SpringApplication.run(CivicPulseApplication.class, args);
    }
}
