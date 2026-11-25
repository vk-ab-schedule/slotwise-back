package ru.vk.itmo.scheduler.slotwise.account;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.vk.itmo.scheduler.slotwise.ApiTestSupport;
import ru.vk.itmo.scheduler.slotwise.TestcontainersConfiguration;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountApiTests {
    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PasswordEncoder passwordEncoder;
    ApiTestSupport api;

    @BeforeEach
    void support() {
        api = new ApiTestSupport(mvc, jdbc, passwordEncoder);
    }

    @Test
    void onlyAdministratorsManageAccounts() throws Exception {
        Cookie organizer = api.login(api.account("ORGANIZER"));

        mvc.perform(get("/api/admin/accounts").cookie(organizer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void createsAccountWithoutPasswordAndRejectsTakenEmailOrExternalId() throws Exception {
        Cookie admin = api.login(api.account("ADMIN"));
        String name = unique();

        long id = readId(send(post("/api/admin/accounts"), admin, profile(name.toUpperCase() + "@Example.org",
                "ext-" + name, "\"role\":\"TEACHER\""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(name + "@example.org"))
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.hasPassword").value(false))
                .andExpect(jsonPath("$.version").value(0)));

        send(post("/api/admin/accounts"), admin, profile(name + "@EXAMPLE.org", null, "\"role\":\"STUDENT\""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"))
                .andExpect(jsonPath("$.accountId").value(id));
        send(post("/api/admin/accounts"), admin, profile(unique() + "@example.org", "ext-" + name, "\"role\":\"STUDENT\""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXTERNAL_ID_TAKEN"))
                .andExpect(jsonPath("$.accountId").value(id));
    }

    @Test
    void searchMatchesPartOfTheNameIgnoringCaseAndFiltersByRole() throws Exception {
        Cookie admin = api.login(api.account("ADMIN"));
        long id = api.accountWithoutPassword("TEACHER");
        String lastName = "Поисков" + id;
        jdbc.update("update slotwise.account set last_name = ? where id = ?", lastName, id);

        mvc.perform(get("/api/admin/accounts").cookie(admin).param("search", "ПОИСКОВ" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.items[0].lastName").value(lastName));
        mvc.perform(get("/api/admin/accounts").cookie(admin).param("search", lastName).param("role", "STUDENT"))
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/admin/accounts").cookie(admin).param("search", "%"))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void profileUpdateRejectsAStaleVersion() throws Exception {
        Cookie admin = api.login(api.account("ADMIN"));
        long id = api.accountWithoutPassword("STUDENT");
        String update = profile(unique() + "@example.org", null, "\"expectedVersion\":0");

        send(patch("/api/admin/accounts/" + id), admin, update)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        send(patch("/api/admin/accounts/" + id), admin, update)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_ACCOUNT_VERSION"));
        mvc.perform(get("/api/admin/accounts/" + Long.MAX_VALUE).cookie(admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void roleChangeEndsSessionsAndAppliesOnNextLogin() throws Exception {
        long adminId = api.account("ADMIN");
        Cookie admin = api.login(adminId);
        long teacherId = api.account("TEACHER");
        Cookie teacher = api.login(teacherId);

        send(put("/api/admin/accounts/" + teacherId + "/role"), admin, "{\"expectedVersion\":0,\"role\":\"ORGANIZER\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ORGANIZER"))
                .andExpect(jsonPath("$.version").value(1));

        mvc.perform(get("/api/auth/me").cookie(teacher)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").cookie(api.login(teacherId))).andExpect(jsonPath("$.role").value("ORGANIZER"));
        send(put("/api/admin/accounts/" + adminId + "/role"), admin, "{\"expectedVersion\":0,\"role\":\"TEACHER\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("OWN_ROLE_CHANGE"));
    }

    @Test
    void disablingEndsSessionsButNeverLeavesTheSystemWithoutAnActiveAdministrator() throws Exception {
        long adminId = api.account("ADMIN");
        Cookie admin = api.login(adminId);
        long otherId = api.account("ADMIN");
        Cookie other = api.login(otherId);

        send(put("/api/admin/accounts/" + otherId + "/enabled"), admin, "{\"expectedVersion\":0,\"enabled\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(get("/api/auth/me").cookie(other)).andExpect(status().isUnauthorized());

        List<Long> disabled = jdbc.queryForList("""
                update slotwise.account set enabled = false where role = 'ADMIN' and enabled and id <> ? returning id
                """, Long.class, adminId);
        try {
            send(put("/api/admin/accounts/" + adminId + "/enabled"), admin, "{\"expectedVersion\":0,\"enabled\":false}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("LAST_ACTIVE_ADMIN"));
        } finally {
            disabled.forEach(id -> jdbc.update("update slotwise.account set enabled = true where id = ?", id));
        }
    }

    @Test
    void newPasswordEndsOtherSessionsAndKeepsTheVersion() throws Exception {
        long adminId = api.account("ADMIN");
        Cookie admin = api.login(adminId);
        Cookie adminElsewhere = api.login(adminId);
        long studentId = api.accountWithoutPassword("STUDENT");

        send(put("/api/admin/accounts/" + studentId + "/password"), admin, "{\"expectedVersion\":0,\"password\":\"" + ApiTestSupport.PASSWORD + "\"}")
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").cookie(api.login(studentId))).andExpect(status().isOk());
        mvc.perform(get("/api/admin/accounts/" + studentId).cookie(admin))
                .andExpect(jsonPath("$.hasPassword").value(true))
                .andExpect(jsonPath("$.version").value(0));

        send(put("/api/admin/accounts/" + adminId + "/password"), admin, "{\"expectedVersion\":0,\"password\":\"another-password\"}")
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").cookie(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").cookie(adminElsewhere)).andExpect(status().isUnauthorized());
        assertThat(passwordEncoder.matches("another-password", jdbc.queryForObject(
                "select password_hash from slotwise.account where id = ?", String.class, adminId))).isTrue();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Cookie session, String body) throws Exception {
        return mvc.perform(request.cookie(session).with(api.xsrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String profile(String email, String externalId, String extra) {
        return """
                {%s, "externalId": %s, "email": "%s", "lastName": "Иванов", "firstName": "Иван",
                 "patronymic": null, "timeZone": "Europe/Moscow"}
                """.formatted(extra, externalId == null ? "null" : "\"" + externalId + "\"", email);
    }

    private static long readId(ResultActions result) throws Exception {
        return JsonPath.<Number>read(result.andReturn().getResponse().getContentAsString(), "$.id").longValue();
    }

    private static String unique() {
        return "account-" + UUID.randomUUID();
    }
}
