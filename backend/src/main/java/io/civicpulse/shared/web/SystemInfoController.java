package io.civicpulse.shared.web;

import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
class SystemInfoController {

    private final ObjectProvider<BuildProperties> buildProperties;
    private final Clock clock;

    SystemInfoController(ObjectProvider<BuildProperties> buildProperties, Clock clock) {
        this.buildProperties = buildProperties;
        this.clock = clock;
    }

    @GetMapping("/info")
    SystemInfoResponse info() {
        BuildProperties build = buildProperties.getIfAvailable();
        String version = build != null ? build.getVersion() : "development";
        return new SystemInfoResponse("CivicPulse", version, "v1", Instant.now(clock));
    }

    record SystemInfoResponse(String name, String version, String apiVersion, Instant serverTime) {
    }
}
