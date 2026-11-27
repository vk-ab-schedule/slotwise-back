package ru.vk.itmo.scheduler.slotwise.account;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import ru.vk.itmo.scheduler.slotwise.ApiTestSupport;
import ru.vk.itmo.scheduler.slotwise.TestcontainersConfiguration;
import ru.vk.itmo.scheduler.slotwise.account.api.dto.SetAccountPasswordRequest;
import ru.vk.itmo.scheduler.slotwise.account.api.dto.UpdateAccountRequest;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

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
    @Autowired
    AccountRepository accounts;
    @Autowired
    AccountService accountService;
    @Autowired
    PlatformTransactionManager transactionManager;
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
        long passwordlessAdmin = api.accountWithoutPassword("ADMIN");
        try {
            send(put("/api/admin/accounts/" + adminId + "/enabled"), admin, "{\"expectedVersion\":0,\"enabled\":false}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("LAST_ACTIVE_ADMIN"));
        } finally {
            jdbc.update("delete from slotwise.account where id = ?", passwordlessAdmin);
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

    @Test
    void rejectsPasswordsLongerThan72Utf8BytesAsAFieldError() throws Exception {
        Cookie admin = api.login(api.account("ADMIN"));
        long target = api.accountWithoutPassword("STUDENT");
        var endpoint = put("/api/admin/accounts/" + target + "/password");
        String body = """
                {"expectedVersion":0,"password":"%s"}
                """;
        send(endpoint, admin, body.formatted("я".repeat(37)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
        send(endpoint, admin, body.formatted("я".repeat(36))).andExpect(status().isNoContent());
    }

    @Test
    void profileUpdateDoesNotOverwritePasswordCommittedAfterItLoadedTheAccount() {
        long adminId = api.account("ADMIN");
        long target = api.account("STUDENT");
        String password = "new-test-password";
        var independent = new TransactionTemplate(transactionManager);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var before = accounts.findById(target).orElseThrow();
            independent.executeWithoutResult(change -> accountService.setPassword(
                    target, adminId, null, new SetAccountPasswordRequest(before.getVersion(), password)));
            accountService.update(target, new UpdateAccountRequest(before.getVersion(), before.getExternalId(),
                    before.getEmail(), "Новое имя", before.getFirstName(), before.getPatronymic(), before.getTimeZone()));
        });

        String hash = jdbc.queryForObject("select password_hash from slotwise.account where id = ?", String.class, target);
        assertThat(passwordEncoder.matches(password, hash)).isTrue();
        assertThat(accounts.findById(target).orElseThrow().getLastName()).isEqualTo("Новое имя");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentIdentityConflictReturnsTheWinningAccountId(boolean updateExternalId) throws Exception {
        Cookie admin = api.login(api.account("ADMIN"));
        long target = api.accountWithoutPassword("STUDENT");
        String accountKey = unique();
        String winnerEmail = accountKey + "@example.org";
        String requestedEmail = updateExternalId ? unique() + "@example.org" : winnerEmail;
        var request = updateExternalId ? patch("/api/admin/accounts/" + target) : post("/api/admin/accounts");
        String body = profile(requestedEmail, updateExternalId ? accountKey : null,
                updateExternalId ? "\"expectedVersion\":0" : "\"role\":\"STUDENT\"");
        var result = new CompletableFuture<ResultActions>();

        Long winner = new TransactionTemplate(transactionManager).execute(status -> {
            int writerPid = jdbc.queryForObject("select pg_backend_pid()", Integer.class);
            Long winnerId = jdbc.queryForObject("""
                    insert into slotwise.account (external_id, email, first_name, last_name, role)
                    values (?, ?, 'Winner', 'Test', 'STUDENT') returning id
                    """, Long.class, accountKey, winnerEmail);
            Thread.ofVirtual().start(() -> {
                try {
                    result.complete(send(request, admin, body));
                } catch (Throwable error) {
                    result.completeExceptionally(error);
                }
            });
            waitForBlockedWrite(writerPid, result);
            return winnerId;
        });

        result.get(10, TimeUnit.SECONDS).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(updateExternalId ? "EXTERNAL_ID_TAKEN" : "EMAIL_TAKEN"))
                .andExpect(jsonPath("$.accountId").value(winner));
    }

    @Test
    void administratorCanPreviewSessionsAffectedByARoleChange() throws Exception {
        Cookie admin = api.login(api.account("ADMIN"));
        long target = api.account("ORGANIZER");
        long otherOrganizer = api.account("ORGANIZER");
        long owned = planningSession(target, unique());
        long member = planningSession(otherOrganizer, unique());
        planningSession(otherOrganizer, unique());
        jdbc.update("insert into slotwise.session_participant (planning_session_id, account_id) values (?, ?)",
                member, target);

        mvc.perform(get("/api/admin/accounts/{id}/role-impact", target).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(owned))
                .andExpect(jsonPath("$[1].id").value(member));
        mvc.perform(get("/api/admin/accounts/{id}/role-impact", target).cookie(api.login(otherOrganizer)))
                .andExpect(status().isForbidden());
        assertThat(accounts.findById(target).orElseThrow().getRole()).isEqualTo(AccountRole.ORGANIZER);
    }

    private long planningSession(long organizer, String name) {
        return jdbc.queryForObject("""
                insert into slotwise.planning_session
                    (organizer_id, name, starts_at, ends_at, collection_starts_at, collection_ends_at)
                values (?, ?, '2027-01-18T09:00Z', '2027-01-18T19:00Z', '2027-01-01T00:00Z', '2027-01-10T00:00Z')
                returning id
                """, Long.class, organizer, name);
    }

    private void waitForBlockedWrite(int writerPid, CompletableFuture<ResultActions> request) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline && !request.isDone()) {
            boolean blocked = Boolean.TRUE.equals(jdbc.queryForObject("""
                    select exists(select 1 from pg_locks where not granted and ? = any(pg_blocking_pids(pid)))
                    """, Boolean.class, writerPid));
            if (blocked) {
                return;
            }
            LockSupport.parkNanos(Duration.ofMillis(10).toNanos());
        }
        throw new AssertionError("Expected the API write to wait on the concurrent transaction");
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
