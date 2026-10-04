package io.civicpulse;

import static org.assertj.core.api.Assertions.assertThat;

import io.civicpulse.shared.web.RequestIdFilter;
import io.civicpulse.support.TestcontainersConfiguration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
@Import(TestcontainersConfiguration.class)
class ApplicationSmokeIT {

    @Autowired
    private TestRestTemplate http;

    @LocalManagementPort
    private int managementPort;

    @Test
    @SuppressWarnings("rawtypes")
    void systemInfoIsPublicAndCarriesARequestId() {
        ResponseEntity<Map> response = http.getForEntity("/api/v1/system/info", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("name", "CivicPulse").containsEntry("apiVersion", "v1");
        assertThat(response.getHeaders().getFirst(RequestIdFilter.HEADER)).isNotBlank();
    }

    @Test
    @SuppressWarnings("rawtypes")
    void unknownApiPathUsesTheErrorModel() {
        ResponseEntity<Map> response = http.getForEntity("/api/v1/nothing-here", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody())
                .containsEntry("code", "RESOURCE_NOT_FOUND")
                .containsEntry("traceId", response.getHeaders().getFirst(RequestIdFilter.HEADER))
                .doesNotContainKeys("trace", "exception");
    }

    @Test
    @SuppressWarnings("rawtypes")
    void healthIsUpOnTheManagementPort() {
        ResponseEntity<Map> response =
                http.getForEntity("http://localhost:" + managementPort + "/actuator/health", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
    }

    @Test
    void actuatorIsNotExposedOnThePublicPort() {
        ResponseEntity<String> response = http.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
