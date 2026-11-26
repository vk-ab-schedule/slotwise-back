package ru.vk.itmo.scheduler.slotwise;

import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Accounts and login sessions for API tests against a real database, the way the SPA works:
 * a {@code SESSION} cookie, and the token of the {@code XSRF-TOKEN} cookie in the {@code X-XSRF-TOKEN} header.
 */
public final class ApiTestSupport {
    public static final String PASSWORD = "api-test-password";

    private final MockMvc mvc;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public ApiTestSupport(MockMvc mvc, JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.mvc = mvc;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * An enabled account with {@link #PASSWORD}.
     */
    public long account(String role) {
        return insertAccount(role, passwordEncoder.encode(PASSWORD));
    }

    public long accountWithoutPassword(String role) {
        return insertAccount(role, null);
    }

    public Cookie login(long accountId) throws Exception {
        String email = jdbc.queryForObject("select email from slotwise.account where id = ?", String.class, accountId);
        return mvc.perform(post("/api/auth/login").with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("SESSION");
    }

    /**
     * The token goes back in the cookie and the header. The {@code csrf()} helper of spring-security-test is not
     * used: it swaps the token repository of the shared filter for a session-based one.
     */
    public RequestPostProcessor xsrf() throws Exception {
        Cookie token = mvc.perform(get("/api/auth/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        return request -> {
            Cookie[] cookies = request.getCookies() == null ? new Cookie[0] : request.getCookies();
            Cookie[] withToken = Arrays.copyOf(cookies, cookies.length + 1);
            withToken[cookies.length] = token;
            request.setCookies(withToken);
            request.addHeader("X-XSRF-TOKEN", token.getValue());
            return request;
        };
    }

    private long insertAccount(String role, String passwordHash) {
        return jdbc.queryForObject("""
                insert into slotwise.account(email, password_hash, first_name, last_name, role)
                values (?, ?, 'Test', 'User', ?) returning id
                """, Long.class, "user-" + UUID.randomUUID() + "@example.org", passwordHash, role);
    }
}
