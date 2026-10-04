package io.civicpulse.shared.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.civicpulse.shared.web.RequestIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest
@Import({ApiErrorResponses.class, ErrorHandlingWebTest.FailingController.class})
class ErrorHandlingWebTest {

    private static final String NOW = "2026-10-03T14:32:07Z";

    @Autowired
    private MockMvc mvc;

    @Test
    void unknownPathReturnsNotFoundInTheErrorModel() throws Exception {
        mvc.perform(get("/api/v1/does-not-exist").header(RequestIdFilter.HEADER, "trace-123"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string(RequestIdFilter.HEADER, "trace-123"))
                .andExpect(jsonPath("$.timestamp").value(NOW))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("The requested resource was not found"))
                .andExpect(jsonPath("$.path").value("/api/v1/does-not-exist"))
                .andExpect(jsonPath("$.traceId").value("trace-123"))
                .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    void invalidBodyListsEveryViolatedField() throws Exception {
        mvc.perform(post("/test/forms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\" \",\"code\":\"TOO-LONG\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0].field").value("code"))
                .andExpect(jsonPath("$.errors[0].code").value("Size"))
                .andExpect(jsonPath("$.errors[1].field").value("title"))
                .andExpect(jsonPath("$.errors[1].code").value("NotBlank"));
    }

    @Test
    void invalidRequestParameterIsAValidationError() throws Exception {
        mvc.perform(get("/test/pages").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("size"))
                .andExpect(jsonPath("$.errors[0].code").value("Min"));
    }

    @Test
    void missingRequestParameterIsAValidationError() throws Exception {
        mvc.perform(get("/test/pages"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("size"))
                .andExpect(jsonPath("$.errors[0].code").value("Required"));
    }

    @Test
    void unreadableJsonIsAMalformedRequest() throws Exception {
        mvc.perform(post("/test/forms").contentType(MediaType.APPLICATION_JSON).content("{\"title\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(content().string(not(containsString("Jackson"))));
    }

    @Test
    void businessErrorKeepsItsCodeStatusAndSafeMessage() throws Exception {
        mvc.perform(get("/test/widgets/42"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WIDGET_LOCKED"))
                .andExpect(jsonPath("$.message").value("Widget 42 is locked"));
    }

    @Test
    void unexpectedErrorRevealsNoInternals() throws Exception {
        mvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(content().string(not(containsString("s3cr3t"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("at io.civicpulse"))));
    }

    @Test
    void unsupportedMethodReturns405WithAllowHeader() throws Exception {
        mvc.perform(delete("/test/forms"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("POST")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unsupportedContentTypeReturns415() throws Exception {
        mvc.perform(post("/test/forms").contentType(MediaType.TEXT_PLAIN).content("title"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse(NOW), ZoneOffset.UTC);
        }
    }

    enum TestErrorCode implements ErrorCode {
        WIDGET_LOCKED;

        @Override
        public String code() {
            return name();
        }

        @Override
        public HttpStatus status() {
            return HttpStatus.CONFLICT;
        }

        @Override
        public String defaultMessage() {
            return "Widget is locked";
        }
    }

    record Form(@NotBlank String title, @Size(max = 5) String code) {
    }

    @RestController
    @RequestMapping("/test")
    static class FailingController {

        @PostMapping("/forms")
        String create(@Valid @RequestBody Form form) {
            return form.title();
        }

        @GetMapping("/pages")
        String page(@RequestParam @Min(1) int size) {
            return "page of " + size;
        }

        @GetMapping("/widgets/{id}")
        String widget(@org.springframework.web.bind.annotation.PathVariable String id) {
            throw new ApiException(TestErrorCode.WIDGET_LOCKED, "Widget " + id + " is locked");
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("connection refused: password=s3cr3t");
        }
    }
}
