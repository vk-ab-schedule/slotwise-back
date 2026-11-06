package ru.vk.itmo.scheduler.slotwise.planning;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ru.vk.itmo.scheduler.slotwise.TestcontainersConfiguration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static ru.vk.itmo.scheduler.slotwise.planning.PlanningApi.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PlanningAvailabilityApiTests.TimeConfiguration.class})
class PlanningAvailabilityApiTests {
    private static final Instant NOW = Instant.parse("2027-01-10T12:00:00Z");
    private static final String PASSWORD = "availability-test-password";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired PlanningAvailabilityService service;

    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration {
        @Bean @Primary
        Clock fixedClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }

    @Test
    void saveNormalizesIntervalsAndKeepsDraftSeparateFromSubmission() throws Exception {
        var f = fixture();
        mvc.perform(put(f.path("busy-intervals/me")).cookie(f.studentCookie()).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                        {"inputVersion":0,"intervals":[
                          {"startsAt":"2027-01-15T10:12:00Z","endsAt":"2027-01-15T10:48:00Z","importance":"LOW"},
                          {"startsAt":"2027-01-15T10:40:00Z","endsAt":"2027-01-15T11:02:00Z","importance":"CRITICAL"}]}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inputVersion").value(1))
                .andExpect(jsonPath("$.responseStatus").value("NOT_RESPONDED"))
                .andExpect(jsonPath("$.intervals.length()").value(2))
                .andExpect(jsonPath("$.intervals[0].startsAt").value("2027-01-15T10:10:00Z"))
                .andExpect(jsonPath("$.intervals[0].endsAt").value("2027-01-15T10:40:00Z"))
                .andExpect(jsonPath("$.intervals[1].endsAt").value("2027-01-15T11:05:00Z"))
                .andExpect(jsonPath("$.warnings").value(org.hamcrest.Matchers.hasItem("STUDENT_CRITICAL_IS_SOFT_FOR_GROUP_EVENTS")));
        assertThat(version(f)).isEqualTo(1);
        mvc.perform(get(f.path("busy-intervals/me")).cookie(f.studentCookie()))
                .andExpect(jsonPath("$.intervals.length()").value(2));
    }

    @Test
    void emptyCalendarCanBeSubmittedAndSubmissionIsIdempotent() throws Exception {
        var f = fixture();
        for (int i = 0; i < 2; i++) {
            mvc.perform(post(f.path("busy-intervals/me/submit")).cookie(f.studentCookie()).with(xsrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.responseStatus").value("SUBMITTED"))
                    .andExpect(jsonPath("$.submittedAt").value(NOW.toString()))
                    .andExpect(jsonPath("$.intervals").isEmpty())
                    .andExpect(jsonPath("$.inputVersion").value(1));
        }
        assertThat(version(f)).isEqualTo(1);
    }

    @Test
    void submittedCalendarCanBeEditedAndClearedWhileOpen() throws Exception {
        var f = fixture();
        service.submit(f.id(), f.student());
        service.saveBusy(f.id(), f.student(), new SaveBusyRequest(1L, List.of(fullDay())));
        mvc.perform(put(f.path("busy-intervals/me")).cookie(f.studentCookie()).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"inputVersion\":2,\"intervals\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.intervals").isEmpty())
                .andExpect(jsonPath("$.inputVersion").value(3));
        assertThat(jdbc.queryForObject("select count(*) from slotwise.busy_interval where planning_session_id=?", Integer.class, f.id())).isZero();
    }

    @Test
    void knownTimetableIsReadOnlyAndDoesNotLeakOtherAccounts() throws Exception {
        var f = fixture();
        long other = account("TEACHER");
        for (long id : new long[]{f.student(), other}) {
            jdbc.update("""
                    insert into slotwise.timetable_entry(account_id, starts_at, ends_at, description)
                    values (?, '2027-01-15T09:00Z', '2027-01-15T19:00Z', 'Class')
                    """, id);
        }
        mvc.perform(put(f.path("busy-intervals/me")).cookie(f.studentCookie()).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"inputVersion\":0,\"intervals\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.knownIntervals.length()").value(1))
                .andExpect(jsonPath("$.warnings").value(org.hamcrest.Matchers.hasItem("NO_FREE_TIME")))
                .andExpect(jsonPath("$.warnings").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("NO_TIMETABLE"))));
        assertThat(jdbc.queryForObject("select count(*) from slotwise.timetable_entry where account_id=?", Integer.class, f.student())).isEqualTo(1);
    }

    @Test
    void collectionListsResponsesAndFlagsFullyBlockedParticipants() throws Exception {
        var f = fixture();
        long teacher = account("TEACHER");
        jdbc.update("insert into slotwise.session_participant(planning_session_id, account_id) values (?, ?)", f.id(), teacher);
        service.saveBusy(f.id(), f.student(), new SaveBusyRequest(0L, List.of(fullDay())));
        service.submit(f.id(), f.student());
        mvc.perform(get(f.path("collection")).cookie(f.ownerCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.participants.length()").value(2))
                .andExpect(jsonPath("$.participants[0].responseStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.participants[0].noFreeTime").value(true))
                .andExpect(jsonPath("$.participants[1].responseStatus").value("NOT_RESPONDED"))
                .andExpect(jsonPath("$.participants[1].noFreeTime").value(false));
    }

    @Test
    void closedCollectionRejectsSaveAndSubmitAndDoesNotStartSolver() throws Exception {
        var f = fixture();
        for (int i = 0; i < 2; i++) {
            mvc.perform(post(f.path("collection/close")).cookie(f.ownerCookie()).with(xsrf()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"))
                    .andExpect(jsonPath("$.inputVersion").value(1));
        }
        mvc.perform(put(f.path("busy-intervals/me")).cookie(f.studentCookie()).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"inputVersion\":1,\"intervals\":[]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COLLECTION_CLOSED"));
        mvc.perform(post(f.path("busy-intervals/me/submit")).cookie(f.studentCookie()).with(xsrf()))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from slotwise.solver_job where planning_session_id=?", Integer.class, f.id())).isZero();
        assertThat(jdbc.queryForObject("select automatic_solve_requested_at from slotwise.planning_session where id=?", Object.class, f.id())).isNull();
    }

    @Test
    void deadlineIsEnforcedEvenIfDatabaseStatusHasNotBeenUpdated() throws Exception {
        var f = fixture();
        jdbc.update("update slotwise.planning_session set collection_ends_at=?, collection_status='OPEN' where id=?",
                java.sql.Timestamp.from(NOW), f.id());
        mvc.perform(get(f.path("busy-intervals/me")).cookie(f.studentCookie()))
                .andExpect(jsonPath("$.collectionStatus").value("CLOSED"));
        mvc.perform(post(f.path("busy-intervals/me/submit")).cookie(f.studentCookie()).with(xsrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COLLECTION_CLOSED"));
    }

    @Test
    void pendingCollectionCannotBeSubmittedOrClosed() throws Exception {
        var f = fixture();
        jdbc.update("update slotwise.planning_session set collection_starts_at='2027-01-11T00:00Z' where id=?", f.id());
        mvc.perform(post(f.path("busy-intervals/me/submit")).cookie(f.studentCookie()).with(xsrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COLLECTION_NOT_OPEN"));
        mvc.perform(post(f.path("collection/close")).cookie(f.ownerCookie()).with(xsrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void extendingDeadlinePreservesAnswersAndRejectsInvalidDates() throws Exception {
        var f = fixture();
        service.submit(f.id(), f.student());
        mvc.perform(patch(f.path("collection")).cookie(f.ownerCookie()).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inputVersion\":1,\"endsAt\":\"2027-01-13T00:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inputVersion").value(2))
                .andExpect(jsonPath("$.participants[0].responseStatus").value("SUBMITTED"));
        for (String end : List.of("2027-01-15T09:00:00Z", "2027-01-09T00:00:00Z")) {
            mvc.perform(patch(f.path("collection")).cookie(f.ownerCookie()).with(xsrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"inputVersion\":2,\"endsAt\":\"" + end + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        service.close(f.id(), f.owner());
        mvc.perform(patch(f.path("collection")).cookie(f.ownerCookie()).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inputVersion\":3,\"endsAt\":\"2027-01-14T00:00:00Z\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void rulesSupportFiveMinuteBufferAndIdempotentReplacement() throws Exception {
        var f = fixture();
        mvc.perform(get(f.path("rules")).cookie(f.ownerCookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.weights.S1").value(1));
        mvc.perform(put(f.path("rules")).cookie(f.ownerCookie()).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content(rulesBody(0, 5)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roomChangeBufferMinutes").value(5))
                .andExpect(jsonPath("$.inputVersion").value(1));
        mvc.perform(put(f.path("rules")).cookie(f.ownerCookie()).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content(rulesBody(1, 5)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.inputVersion").value(1));
        mvc.perform(get(f.path("rules")).cookie(f.ownerCookie()))
                .andExpect(jsonPath("$.weights.S3").value(3)).andExpect(jsonPath("$.seed").value(42));
        assertThat(version(f)).isEqualTo(1);
    }

    @Test
    void invalidRulesAreRejectedWithoutChangingDatabase() throws Exception {
        var f = fixture();
        for (String body : List.of(rulesBody(0, 7), rulesBody(0, 5).replace("\"S1\":1", "\"S6\":1"),
                rulesBody(0, 5).replace("\"S1\":1", "\"S1\":-1"),
                rulesBody(0, 5).replace("\"timeLimitSeconds\":60", "\"timeLimitSeconds\":0"))) {
            mvc.perform(put(f.path("rules")).cookie(f.ownerCookie()).with(xsrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(version(f)).isZero();
    }

    @Test
    void invalidCalendarDoesNotDeletePreviouslySavedData() throws Exception {
        var f = fixture();
        service.saveBusy(f.id(), f.student(), new SaveBusyRequest(0L, List.of(fullDay())));
        for (String body : List.of("{\"inputVersion\":1,\"intervals\":[null]}", """
                {"inputVersion":1,"intervals":[{"startsAt":"2027-01-15T12:00:00Z",
                "endsAt":"2027-01-15T11:00:00Z","importance":"LOW"}]}
                """)) {
            mvc.perform(put(f.path("busy-intervals/me")).cookie(f.studentCookie()).with(xsrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(version(f)).isEqualTo(1);
        assertThat(service.busy(f.id(), f.student()).intervals()).containsExactly(fullDay());
    }

    @Test
    void staleVersionIsRejectedWithProblemDetails() throws Exception {
        var f = fixture();
        service.submit(f.id(), f.student());
        mvc.perform(put(f.path("rules")).cookie(f.ownerCookie()).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content(rulesBody(0, 15)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("STALE_INPUT_VERSION"));
        assertThat(version(f)).isEqualTo(1);
    }

    @Test
    void accessIsRestrictedToParticipantAndOwningOrganizer() throws Exception {
        var f = fixture();
        Cookie outsider = login(account("STUDENT"));
        Cookie otherOrganizer = login(account("ORGANIZER"));
        mvc.perform(get(f.path("busy-intervals/me"))).andExpect(status().isUnauthorized());
        mvc.perform(get(f.path("busy-intervals/me")).cookie(outsider)).andExpect(status().isForbidden());
        mvc.perform(get(f.path("collection")).cookie(f.studentCookie())).andExpect(status().isForbidden());
        mvc.perform(get(f.path("collection")).cookie(otherOrganizer)).andExpect(status().isForbidden());
        mvc.perform(get(f.path("rules")).cookie(otherOrganizer)).andExpect(status().isForbidden());
        mvc.perform(post(f.path("collection/close")).cookie(otherOrganizer).with(xsrf())).andExpect(status().isForbidden());
        mvc.perform(put(f.path("rules")).cookie(f.studentCookie()).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content(rulesBody(0, 15)))
                .andExpect(status().isForbidden());
        mvc.perform(post(f.path("busy-intervals/me/submit")).cookie(f.studentCookie()))
                .andExpect(status().isForbidden()); // CSRF remains mandatory.
        mvc.perform(get("/api/sessions/9223372036854775807/rules").cookie(f.ownerCookie()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
        assertThat(version(f)).isZero();
    }

    @Test
    void concurrentWritesCannotLoseInputVersionOrOverwriteStaleData() throws Exception {
        var f = fixture();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var tasks = List.of(
                    pool.submit(() -> raceSave(f, start)),
                    pool.submit(() -> raceSave(f, start)));
            start.countDown();
            assertThat(List.of(tasks.get(0).get(10, TimeUnit.SECONDS), tasks.get(1).get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("saved", "STALE_INPUT_VERSION");
        }
        assertThat(version(f)).isEqualTo(1);
        assertThat(service.busy(f.id(), f.student()).intervals()).containsExactly(fullDay());
    }

    @Test
    void teacherCanSubmitButCannotReadOrganizersCollection() throws Exception {
        var f = fixture();
        long teacher = account("TEACHER");
        jdbc.update("insert into slotwise.session_participant(planning_session_id,account_id) values (?, ?)", f.id(), teacher);
        Cookie cookie = login(teacher);
        mvc.perform(post(f.path("busy-intervals/me/submit")).cookie(cookie).with(xsrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.responseStatus").value("SUBMITTED"));
        mvc.perform(get(f.path("collection")).cookie(cookie)).andExpect(status().isForbidden());
    }

    @Test
    void openApiContainsAllPlanningOperations() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/sessions/{id}/busy-intervals/me'].get").exists())
                .andExpect(jsonPath("$.paths['/api/sessions/{id}/busy-intervals/me'].put").exists())
                .andExpect(jsonPath("$.paths['/api/sessions/{id}/busy-intervals/me/submit'].post").exists())
                .andExpect(jsonPath("$.paths['/api/sessions/{id}/collection'].get").exists())
                .andExpect(jsonPath("$.paths['/api/sessions/{id}/collection'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/sessions/{id}/collection/close'].post").exists())
                .andExpect(jsonPath("$.paths['/api/sessions/{id}/rules'].get").exists())
                .andExpect(jsonPath("$.paths['/api/sessions/{id}/rules'].put").exists());
    }

    private String raceSave(Fixture f, CountDownLatch start) throws InterruptedException {
        start.await();
        try {
            service.saveBusy(f.id(), f.student(), new SaveBusyRequest(0L, List.of(fullDay())));
            return "saved";
        } catch (PlanningException e) {
            return e.getCode();
        }
    }

    private Fixture fixture() throws Exception {
        long owner = account("ORGANIZER");
        long student = account("STUDENT");
        long id = jdbc.queryForObject("""
                insert into slotwise.planning_session(organizer_id,name,starts_at,ends_at,time_zone,
                    collection_starts_at,collection_ends_at)
                values (?, 'B5', '2027-01-15T09:00Z','2027-01-15T19:00Z','UTC',
                    '2027-01-09T00:00Z','2027-01-12T00:00Z') returning id
                """, Long.class, owner);
        jdbc.update("insert into slotwise.session_working_day values (?, 5)", id);
        jdbc.update("insert into slotwise.session_participant(planning_session_id,account_id) values (?, ?)", id, student);
        return new Fixture(id, owner, student, login(owner), login(student));
    }

    private long account(String role) {
        return jdbc.queryForObject("""
                insert into slotwise.account(email,password_hash,first_name,last_name,role)
                values (?, ?, 'Test', 'Participant', ?) returning id
                """, Long.class, UUID.randomUUID() + "@example.org", encoder.encode(PASSWORD), role);
    }

    private Cookie login(long id) throws Exception {
        String email = jdbc.queryForObject("select email from slotwise.account where id=?", String.class, id);
        return mvc.perform(post("/api/auth/login").with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
    }

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

    private long version(Fixture f) {
        return jdbc.queryForObject("select input_version from slotwise.planning_session where id=?", Long.class, f.id());
    }

    private static Interval fullDay() {
        return new Interval(Instant.parse("2027-01-15T09:00:00Z"), Instant.parse("2027-01-15T19:00:00Z"), BusyImportance.CRITICAL);
    }

    private static String rulesBody(long version, int buffer) {
        return """
                {"inputVersion":%d,"weights":{"S1":1,"S2":2,"S3":3,"S4":4,"S5":5},
                "roomChangeBufferMinutes":%d,"defaultAlgorithm":"GREEDY","timeLimitSeconds":60,"seed":42}
                """.formatted(version, buffer);
    }

    private record Fixture(long id, long owner, long student, Cookie ownerCookie, Cookie studentCookie) {
        String path(String suffix) { return "/api/sessions/" + id + "/" + suffix; }
    }
}
