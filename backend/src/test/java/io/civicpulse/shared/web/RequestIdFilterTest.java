package io.civicpulse.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void generatesAnIdWhenNoneIsSent() throws Exception {
        MockHttpServletResponse response = run(new MockHttpServletRequest(), new AtomicReference<>());

        String id = response.getHeader(RequestIdFilter.HEADER);
        assertThat(id).isNotBlank();
        assertThat(UUID.fromString(id)).isNotNull();
    }

    @Test
    void reusesAWellFormedInboundId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "web-7f3a.retry_2");

        MockHttpServletResponse response = run(request, new AtomicReference<>());

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("web-7f3a.retry_2");
        assertThat(RequestIdFilter.currentId(request)).isEqualTo("web-7f3a.retry_2");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "has space", "<script>", "line\nbreak", "x".repeat(65)})
    void replacesMalformedInboundIds(String malformed) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, malformed);

        MockHttpServletResponse response = run(request, new AtomicReference<>());

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isNotEqualTo(malformed);
        assertThat(UUID.fromString(response.getHeader(RequestIdFilter.HEADER))).isNotNull();
    }

    @Test
    void exposesTheIdToLoggingOnlyForTheDurationOfTheRequest() throws Exception {
        AtomicReference<String> seenInChain = new AtomicReference<>();

        MockHttpServletResponse response = run(new MockHttpServletRequest(), seenInChain);

        assertThat(seenInChain.get()).isEqualTo(response.getHeader(RequestIdFilter.HEADER));
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    private MockHttpServletResponse run(MockHttpServletRequest request, AtomicReference<String> mdcInChain)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                mdcInChain.set(MDC.get(RequestIdFilter.MDC_KEY));
            }
        };
        filter.doFilter(request, response, chain);
        return response;
    }
}
