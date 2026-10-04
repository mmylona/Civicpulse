package io.civicpulse.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

public final class AuthTestClient {

    public static final String REFRESH_COOKIE = "civicpulse_refresh";
    public static final String PASSWORD = "correct-horse-battery";

    private static final AtomicInteger ADDRESSES = new AtomicInteger();

    private final MockMvc mvc;

    public AuthTestClient(MockMvc mvc) {
        this.mvc = mvc;
    }

    public static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@test.local";
    }

    public static RequestPostProcessor fromNewAddress() {
        int n = ADDRESSES.incrementAndGet();
        String address = "10.%d.%d.%d".formatted((n >> 16) & 255, (n >> 8) & 255, n & 255);
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    public static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    public Session register(String email) throws Exception {
        return register(email, PASSWORD, "Test User");
    }

    public Session register(String email, String password, String displayName) throws Exception {
        String body = """
                {"email": "%s", "password": "%s", "displayName": "%s"}
                """.formatted(email, password, displayName);
        MockHttpServletResponse response = mvc.perform(json(post("/api/v1/auth/register"), body).with(fromNewAddress()))
                .andReturn().getResponse();
        return Session.from(response, 201);
    }

    public Session login(String email) throws Exception {
        return login(email, PASSWORD);
    }

    public Session login(String email, String password) throws Exception {
        String body = """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
        MockHttpServletResponse response = mvc.perform(json(post("/api/v1/auth/login"), body).with(fromNewAddress()))
                .andReturn().getResponse();
        return Session.from(response, 200);
    }

    public record Session(UUID userId, String accessToken, Cookie refreshCookie) {

        static Session from(MockHttpServletResponse response, int expectedStatus) throws Exception {
            String content = response.getContentAsString();
            if (response.getStatus() != expectedStatus) {
                throw new AssertionError("Expected " + expectedStatus + " but got " + response.getStatus() + ": " + content);
            }
            Map<String, Object> user = JsonPath.read(content, "$.user");
            return new Session(
                    UUID.fromString((String) user.get("id")),
                    JsonPath.read(content, "$.accessToken"),
                    response.getCookie(REFRESH_COOKIE));
        }

        public String bearer() {
            return "Bearer " + accessToken;
        }
    }
}
