package io.civicpulse;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Enforces ADR-001: modules only use each other's published API and events.
 * A boundary violation fails this test, and therefore the build.
 */
class ModularityTests {

    private static final ApplicationModules MODULES = ApplicationModules.of(CivicPulseApplication.class);

    @Test
    void moduleBoundariesAreRespected() {
        MODULES.verify();
    }

    /** Renders module diagrams (PlantUML / C4) into target/spring-modulith-docs. */
    @Test
    void writesModuleDocumentation() {
        new Documenter(MODULES).writeDocumentation();
    }
}
