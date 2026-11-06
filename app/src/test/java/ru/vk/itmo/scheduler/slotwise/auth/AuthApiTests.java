package ru.vk.itmo.scheduler.slotwise.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ru.vk.itmo.scheduler.slotwise.TestcontainersConfiguration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "slotwise.admin.email=" + AuthApiTests.ADMIN_EMAIL,
        "slotwise.admin.password=" + AuthApiTests.ADMIN_PASSWORD})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthApiTests {
    static final String ADMIN_EMAIL = "first-admin@example.org";
    static final String ADMIN_PASSWORD = "first-admin-password";
    private static final String PASSWORD = "correct-password";

    @Autowired
    MockMvc mvc;
    @LocalServerPort
    int port;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void loginStartsSessionAndReturnsCurrentUser() throws Exception {
        long id = account("TEACHER", PASSWORD, true);
        String email = emailOf(id);

        Cookie session = login(email, PASSWORD);

        mvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andExpect(jsonPath("$.timeZone").value("Europe/Moscow"));
    }

    @Test
    void cookiesHaveBrowserFlagsOnRealServer() throws Exception {
        long id = account("STUDENT", PASSWORD, true);
        HttpClient http = HttpClient.newHttpClient();
        URI base = URI.create("http://localhost:" + port);

        HttpResponse<Void> anonymous = http.send(HttpRequest.newBuilder(base.resolve("/api/auth/me")).build(),
                HttpResponse.BodyHandlers.discarding());
        String csrfCookie = setCookie(anonymous, "XSRF-TOKEN");
        assertThat(csrfCookie).contains("SameSite=Lax").doesNotContain("HttpOnly");
        String csrfToken = csrfCookie.substring("XSRF-TOKEN=".length(), csrfCookie.indexOf(';'));

        HttpResponse<Void> login = http.send(HttpRequest.newBuilder(base.resolve("/api/auth/login"))
                        .header("Content-Type", "application/json")
                        .header("Cookie", "XSRF-TOKEN=" + csrfToken)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .POST(HttpRequest.BodyPublishers.ofString(loginJson(emailOf(id), PASSWORD)))
                        .build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(setCookie(login, "SESSION")).contains("HttpOnly", "SameSite=Lax");
        assertThat(setCookie(login, "XSRF-TOKEN")).doesNotContain(csrfToken);
    }

    @Test
    void sessionIsIndexedByAccountId() throws Exception {
        long id = account("STUDENT", PASSWORD, true);

        login(emailOf(id), PASSWORD);

        Integer sessions = jdbc.queryForObject(
                "select count(*) from slotwise.spring_session where principal_name = ?", Integer.class, Long.toString(id));
        assertThat(sessions).isEqualTo(1);
    }

    @Test
    void emailIgnoresCaseAndSurroundingSpaces() throws Exception {
        long id = account("ORGANIZER", PASSWORD, true);

        mvc.perform(post("/api/auth/login").with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("  " + emailOf(id).toUpperCase() + " ", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameError() throws Exception {
        long id = account("STUDENT", PASSWORD, true);

        expectLoginError(emailOf(id), "wrong-password", "BAD_CREDENTIALS");
        expectLoginError("nobody-" + UUID.randomUUID() + "@example.org", PASSWORD, "BAD_CREDENTIALS");
    }

    @Test
    void accountWithoutPasswordCannotLogIn() throws Exception {
        long id = account("STUDENT", null, true);

        expectLoginError(emailOf(id), PASSWORD, "BAD_CREDENTIALS");
    }

    @Test
    void disabledAccountIsReportedOnlyAfterCorrectPassword() throws Exception {
        long id = account("TEACHER", PASSWORD, false);

        expectLoginError(emailOf(id), PASSWORD, "ACCOUNT_DISABLED");
        expectLoginError(emailOf(id), "wrong-password", "BAD_CREDENTIALS");
    }

    @Test
    void anonymousRequestGetsProblemAndCsrfCookie() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().doesNotExist("SESSION"));
    }

    @Test
    void unknownApiPathRequiresLogin() throws Exception {
        mvc.perform(get("/api/sessions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithoutCsrfTokenIsRejected() throws Exception {
        long id = account("STUDENT", PASSWORD, true);

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(emailOf(id), PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void invalidLoginBodyIsBadRequest() throws Exception {
        mvc.perform(post("/api/auth/login").with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logoutEndsSessionOnServer() throws Exception {
        long id = account("ORGANIZER", PASSWORD, true);
        Cookie session = login(emailOf(id), PASSWORD);

        mvc.perform(post("/api/auth/logout").with(xsrf()).cookie(session))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isUnauthorized());
        Integer sessions = jdbc.queryForObject(
                "select count(*) from slotwise.spring_session where principal_name = ?", Integer.class, Long.toString(id));
        assertThat(sessions).isZero();
    }

    @Test
    void firstAdministratorIsCreatedFromProperties() throws Exception {
        String hash = jdbc.queryForObject(
                "select password_hash from slotwise.account where email = ? and role = 'ADMIN'", String.class, ADMIN_EMAIL);
        assertThat(hash).startsWith("{bcrypt}");

        Cookie session = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        mvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void healthAndApiDocsArePublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    private Cookie login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, password)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("SESSION"))
                .andReturn();
        return result.getResponse().getCookie("SESSION");
    }

    /**
     * Real CSRF exchange, as the SPA does it: the token from the {@code XSRF-TOKEN} cookie goes back
     * in the cookie and in the header. The {@code csrf()} helper of spring-security-test is not used:
     * it swaps the token repository of the shared filter for a session-based one.
     */
    private RequestPostProcessor xsrf() throws Exception {
        Cookie token = mvc.perform(get("/api/auth/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(token).isNotNull();
        return request -> {
            Cookie[] cookies = request.getCookies() == null ? new Cookie[0] : request.getCookies();
            Cookie[] withToken = Arrays.copyOf(cookies, cookies.length + 1);
            withToken[cookies.length] = token;
            request.setCookies(withToken);
            request.addHeader("X-XSRF-TOKEN", token.getValue());
            return request;
        };
    }

    private static String setCookie(HttpResponse<?> response, String name) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(cookie -> cookie.startsWith(name + "=") && !cookie.startsWith(name + "=;"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No " + name + " cookie in " + response.headers()));
    }

    private void expectLoginError(String email, String password, String code) throws Exception {
        mvc.perform(post("/api/auth/login").with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, password)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(cookie().doesNotExist("SESSION"));
    }

    private long account(String role, String password, boolean enabled) {
        String hash = password == null ? null : passwordEncoder.encode(password);
        return jdbc.queryForObject("""
                insert into slotwise.account(email, password_hash, first_name, last_name, role, enabled)
                values (?, ?, 'Test', 'User', ?, ?) returning id
                """, Long.class, UUID.randomUUID() + "@example.org", hash, role, enabled);
    }

    private String emailOf(long id) {
        return jdbc.queryForObject("select email from slotwise.account where id = ?", String.class, id);
    }

    private static String loginJson(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }
}
