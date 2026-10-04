package io.civicpulse;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTests {

    private static final ApplicationModules MODULES = ApplicationModules.of(CivicPulseApplication.class);

    @Test
    void moduleBoundariesAreRespected() {
        MODULES.verify();
    }

    @Test
    void writesModuleDocumentation() {
        new Documenter(MODULES).writeDocumentation();
    }
}
