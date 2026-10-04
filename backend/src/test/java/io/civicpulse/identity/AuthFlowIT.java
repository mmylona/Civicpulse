package io.civicpulse.identity;

import static io.civicpulse.support.AuthTestClient.PASSWORD;
import static io.civicpulse.support.AuthTestClient.REFRESH_COOKIE;
import static io.civicpulse.support.AuthTestClient.fromNewAddress;
import static io.civicpulse.support.AuthTestClient.json;
import static io.civicpulse.support.AuthTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.civicpulse.shared.security.JwtProperties;
import io.civicpulse.support.AuthTestClient;
import io.civicpulse.support.AuthTestClient.Session;
import io.civicpulse.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@IntegrationTest
class AuthFlowIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    private AuthTestClient auth;

    @BeforeEach
    void setUp() {
        auth = new AuthTestClient(mvc);
    }

    @Test
    void registerReturnsTokenAndRefreshCookie() throws Exception {
        String email = uniqueEmail();

        mvc.perform(json(post("/api/v1/auth/register"), """
                        {"email": "%s", "password": "%s", "displayName": "  Ada Citizen "}
                        """.formatted(email.toUpperCase(), PASSWORD)).with(fromNewAddress()))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.displayName").value("Ada Citizen"))
                .andExpect(jsonPath("$.user.roles").value(List.of("CITIZEN")))
                .andExpect(jsonPath("$.user.team").value(nullValue()))
                .andExpect(cookie().httpOnly(REFRESH_COOKIE, true))
                .andExpect(cookie().secure(REFRESH_COOKIE, true))
                .andExpect(cookie().sameSite(REFRESH_COOKIE, "Strict"))
                .andExpect(cookie().path(REFRESH_COOKIE, "/api/v1/auth"))
                .andExpect(cookie().maxAge(REFRESH_COOKIE, 14 * 24 * 3600));
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        String email = uniqueEmail();
        auth.register(email);

        mvc.perform(json(post("/api/v1/auth/register"), """
                        {"email": " %s ", "password": "%s", "displayName": "Someone Else"}
                        """.formatted(email.toUpperCase(), PASSWORD)).with(fromNewAddress()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void registerValidation() throws Exception {
        mvc.perform(json(post("/api/v1/auth/register"), """
                        {"email": "not-an-email", "password": "short", "displayName": " "}
                        """).with(fromNewAddress()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItems("email", "password", "displayName")))
                .andExpect(jsonPath("$.errors[?(@.field == 'password')].code").value("ValidPassword"));
    }

    @Test
    void wrongPasswordAndUnknownEmailLookTheSame() throws Exception {
        String email = uniqueEmail();
        auth.register(email);

        for (String body : List.of(
                "{\"email\": \"%s\", \"password\": \"wrong-password-123\"}".formatted(email),
                "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(uniqueEmail(), PASSWORD))) {
            mvc.perform(json(post("/api/v1/auth/login"), body).with(fromNewAddress()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(cookie().doesNotExist(REFRESH_COOKIE));
        }
    }

    @Test
    void accountLocksAfterFiveFailures() throws Exception {
        String email = uniqueEmail();
        UUID userId = auth.register(email).userId();
        RequestPostProcessor sameClient = fromNewAddress();

        for (int attempt = 1; attempt <= 5; attempt++) {
            mvc.perform(json(post("/api/v1/auth/login"),
                            "{\"email\": \"%s\", \"password\": \"wrong-password-123\"}".formatted(email))
                            .with(sameClient))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(json(post("/api/v1/auth/login"),
                        "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, PASSWORD)).with(sameClient))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        assertThat(auditActionsFor(userId))
                .containsSubsequence("SIGN_IN_FAILED", "SIGN_IN_FAILED", "SIGN_IN_FAILED", "SIGN_IN_FAILED", "ACCOUNT_LOCKED");
    }

    @Test
    void loginIsRateLimited() throws Exception {
        RequestPostProcessor sameClient = fromNewAddress();
        String body = "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(uniqueEmail(), PASSWORD);

        for (int attempt = 1; attempt <= 10; attempt++) {
            mvc.perform(json(post("/api/v1/auth/login"), body).with(sameClient)).andExpect(status().isUnauthorized());
        }
        mvc.perform(json(post("/api/v1/auth/login"), body).with(sameClient))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void meReturnsCurrentUser() throws Exception {
        String email = uniqueEmail();
        Session session = auth.register(email);

        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(session.userId().toString()))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.roles").value(List.of("CITIZEN")));
    }

    @Test
    void missingOrInvalidToken() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.traceId").isString());

        mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")))
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    void expiredOrForeignTokenIsRejected() throws Exception {
        UUID userId = auth.register(uniqueEmail()).userId();
        Instant now = Instant.now();

        String expired = token(userId, jwtProperties.issuer(), now.minus(30, ChronoUnit.MINUTES), now.minus(5, ChronoUnit.MINUTES));
        String foreign = token(userId, "someone-else", now, now.plus(5, ChronoUnit.MINUTES));

        for (String token : List.of(expired, foreign)) {
            mvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        }
    }

    @Test
    void refreshRotatesToken() throws Exception {
        Session session = auth.register(uniqueEmail());

        String newCookie = mvc.perform(post("/api/v1/auth/refresh").cookie(session.refreshCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(session.userId().toString()))
                .andExpect(jsonPath("$.accessToken").isString())
                .andReturn().getResponse().getCookie(REFRESH_COOKIE).getValue();

        assertThat(newCookie).isNotEqualTo(session.refreshCookie().getValue());
    }

    @Test
    void reusedRefreshTokenRevokesFamily() throws Exception {
        Session session = auth.register(uniqueEmail());
        Cookie first = session.refreshCookie();
        Cookie second = mvc.perform(post("/api/v1/auth/refresh").cookie(first))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie(REFRESH_COOKIE);

        mvc.perform(post("/api/v1/auth/refresh").cookie(first))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        mvc.perform(post("/api/v1/auth/refresh").cookie(second))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        assertThat(auditActionsFor(session.userId())).contains("REFRESH_TOKEN_REUSE_DETECTED");
    }

    @Test
    void refreshWithoutCookie() throws Exception {
        mvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        Session session = auth.register(uniqueEmail());

        mvc.perform(post("/api/v1/auth/logout").cookie(session.refreshCookie()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(REFRESH_COOKIE, 0));

        mvc.perform(post("/api/v1/auth/refresh").cookie(session.refreshCookie()))
                .andExpect(status().isUnauthorized());
    }

    private String token(UUID userId, String issuer, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", List.of("CITIZEN"))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private List<String> auditActionsFor(UUID userId) {
        return jdbc.sql("SELECT action FROM audit_entries WHERE target_id = :id ORDER BY occurred_at, id")
                .param("id", userId.toString())
                .query(String.class)
                .list();
    }
}
