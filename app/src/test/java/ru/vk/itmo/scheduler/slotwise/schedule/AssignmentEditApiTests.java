package ru.vk.itmo.scheduler.slotwise.schedule;

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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.vk.itmo.scheduler.slotwise.ApiTestSupport;
import ru.vk.itmo.scheduler.slotwise.TestcontainersConfiguration;
import ru.vk.itmo.scheduler.slotwise.solving.SessionFixture;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AssignmentEditApiTests {
    private static final String THURSDAY = "2027-01-21T15:00:00Z";

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    PlatformTransactionManager transactions;
    ApiTestSupport api;
    SessionFixture f;
    Cookie organizer;
    long event;
    long variant;

    @BeforeEach
    void fixture() throws Exception {
        api = new ApiTestSupport(mvc, jdbc, passwordEncoder);
        f = new SessionFixture(api, jdbc);
        organizer = api.login(f.organizer);
        event = f.consultation(true);
        variant = f.draft(mvc, api, organizer, 1);
    }

    @Test
    void previewThenSaveALockedMoveThatTheNextCalculationKeeps() throws Exception {
        edit(true, 0, 0, assigned(THURSDAY, "2027-01-21T15:30:00Z", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.variantVersion").value(0))
                .andExpect(jsonPath("$.assignment.startsAt").value(THURSDAY))
                .andExpect(jsonPath("$.assignment.room.id").value(f.room))
                .andExpect(jsonPath("$.metrics.hardViolations").value(0));
        assertThat(startOf(variant)).isNotEqualTo(Instant.parse(THURSDAY));

        edit(false, 0, 0, assigned(THURSDAY, "2027-01-21T15:30:00Z", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variantVersion").value(1))
                .andExpect(jsonPath("$.assignment.version").value(1))
                .andExpect(jsonPath("$.assignment.locked").value(true))
                .andExpect(jsonPath("$.metrics.solveStatus").value("FEASIBLE"));
        assertThat(startOf(variant)).isEqualTo(Instant.parse(THURSDAY));

        String job = mvc.perform(post(f.path("solve")).cookie(organizer).with(api.xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"expectedInputVersion": 0, "algorithm": "GREEDY", "timeLimitSeconds": 1, "seed": 5,
                                 "baseVariantId": %d}
                                """.formatted(variant)))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertThat(job).contains("\"baseVariantId\":" + variant);
    }

    @Test
    void anEditBreakingAHardConstraintIsRejectedWithItsReason() throws Exception {
        f.classes(f.teacher, "2027-01-18T09:00Z", "2027-01-18T10:30Z");

        edit(false, 0, 0, assigned("2027-01-18T09:00:00Z", "2027-01-18T09:30:00Z", false))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("HARD_VIOLATIONS"))
                .andExpect(jsonPath("$.issues[0].code").value("PERSON_HAS_CLASSES"))
                .andExpect(jsonPath("$.issues[0].accountIds[0]").value(f.teacher));
        edit(false, 0, 0, assigned(THURSDAY, "2027-01-21T16:00:00Z", false))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_ASSIGNMENT"));
        assertThat(startOf(variant)).isNotEqualTo(Instant.parse("2027-01-18T09:00:00Z"));
    }

    @Test
    void unassigningCountsInTheMetricsAndStaleVersionsOrPublishedVariantsAreRejected() throws Exception {
        String unassigned = """
                {"expectedVariantVersion": %d, "expectedAssignmentVersion": %d, "status": "UNASSIGNED",
                 "startsAt": null, "endsAt": null, "roomId": null, "commissionMembers": [], "locked": false}
                """;
        edit(false, 0, 0, unassigned).andExpect(status().isOk())
                .andExpect(jsonPath("$.assignment.status").value("UNASSIGNED"))
                .andExpect(jsonPath("$.metrics.unassignedEvents").value(1));
        edit(false, 0, 0, unassigned).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VARIANT_VERSION"));

        mvc.perform(post("/api/variants/" + variant + "/publish").cookie(organizer).with(api.xsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\": 1, \"confirmUnassigned\": true, \"confirmStaleData\": false}"))
                .andExpect(status().isOk());
        edit(false, 2, 1, assigned(THURSDAY, "2027-01-21T15:30:00Z", false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VARIANT_NOT_DRAFT"));
    }

    @Test
    void rejectsDuplicateCommissionMembersBeforeValidation() throws Exception {
        String members = """
                [{"accountId": %d, "chair": false}, {"accountId": %d, "chair": true}]
                """.formatted(f.teacher, f.teacher);
        String request = assigned(THURSDAY, "2027-01-21T15:30:00Z", false).replace("[]", members);

        edit(false, 0, 0, request).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_ASSIGNMENT"));
    }

    @Test
    void aPartialRepairReportsAnOutOfPeriodAssignmentAndCannotBePublished() throws Exception {
        long otherEvent = f.consultation(true);
        variant = f.draft(mvc, api, organizer, 2);
        jdbc.update("""
                update slotwise.assignment set starts_at = '2027-01-21T15:00Z', ends_at = '2027-01-21T15:30Z'
                where schedule_variant_id = ? and event_id = ?
                """, variant, otherEvent);
        jdbc.update("update slotwise.planning_session set ends_at = '2027-01-20T00:00Z', input_version = 1 where id = ?",
                f.session);
        String move = assigned("2027-01-19T14:00:00Z", "2027-01-19T14:30:00Z", false);

        edit(true, 0, 0, move).andExpect(status().isOk())
                .andExpect(jsonPath("$.variantVersion").value(0))
                .andExpect(jsonPath("$.metrics.unassignedEvents").value(0))
                .andExpect(jsonPath("$.metrics.hardViolations").value(1))
                .andExpect(jsonPath("$.issues[0].eventIds[0]").value(otherEvent));
        edit(false, 0, 0, move).andExpect(status().isOk())
                .andExpect(jsonPath("$.variantVersion").value(1))
                .andExpect(jsonPath("$.metrics.unassignedEvents").value(0))
                .andExpect(jsonPath("$.metrics.hardViolations").value(1))
                .andExpect(jsonPath("$.metrics.solveStatus").value("NO_SOLUTION"))
                .andExpect(jsonPath("$.issues[0].eventIds[0]").value(otherEvent));
        mvc.perform(get("/api/variants/" + variant).cookie(organizer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics.solveStatus").value("NO_SOLUTION"))
                .andExpect(jsonPath("$.metrics.unassignedEvents").value(0))
                .andExpect(jsonPath("$.metrics.hardViolations").value(1))
                .andExpect(jsonPath("$.issues[0].eventIds[0]").value(otherEvent));
        mvc.perform(post("/api/variants/" + variant + "/publish").cookie(organizer).with(api.xsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion": 1, "confirmUnassigned": true, "confirmStaleData": true}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("HARD_VIOLATIONS"))
                .andExpect(jsonPath("$.issues[0].eventIds[0]").value(otherEvent));

        event = otherEvent;
        String repaired = assigned("2027-01-19T15:00:00Z", "2027-01-19T15:30:00Z", false);
        edit(false, 1, 0, repaired).andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics.hardViolations").value(0))
                .andExpect(jsonPath("$.metrics.solveStatus").value("FEASIBLE"))
                .andExpect(jsonPath("$.issues").isEmpty());
    }

    @Test
    void validatesAnEditAgainstInputChangesCommittedWhileItWaitsForTheSession() throws Exception {
        String move = assigned(THURSDAY, "2027-01-21T15:30:00Z", false);
        try (var callers = Executors.newVirtualThreadPerTaskExecutor()) {
            var request = new TransactionTemplate(transactions).execute(transaction -> {
                int blocker = jdbc.queryForObject("select pg_backend_pid()", Integer.class);
                jdbc.queryForObject("select id from slotwise.planning_session where id = ? for update", Long.class,
                        f.session);
                var pending = callers.submit(() -> edit(false, 0, 0, move));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean waiting = false;
                while (!pending.isDone() && System.nanoTime() < deadline) {
                    waiting = jdbc.queryForObject("""
                            select exists(select 1 from pg_locks where not granted and ? = any(pg_blocking_pids(pid)))
                            """, Boolean.class, blocker);
                    if (waiting) {
                        break;
                    }
                }
                assertThat(waiting).as("the edit waits for the session lock").isTrue();
                f.classes(f.teacher, "2027-01-21T15:00Z", "2027-01-21T15:30Z");
                jdbc.update("update slotwise.planning_session set input_version = 1 where id = ?", f.session);
                return pending;
            });

            request.get(10, TimeUnit.SECONDS).andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("HARD_VIOLATIONS"))
                    .andExpect(jsonPath("$.issues[0].code").value("PERSON_HAS_CLASSES"));
            assertThat(startOf(variant)).isNotEqualTo(Instant.parse(THURSDAY));
        }
    }

    @Test
    void assignsAnEventAddedAfterCalculationOnlyWhenTheEditIsSaved() throws Exception {
        event = f.consultation(true);
        jdbc.update("update slotwise.planning_session set input_version = input_version + 1 where id = ?", f.session);
        String move = assigned(THURSDAY, "2027-01-21T15:30:00Z", true);

        edit(true, 0, 0, move).andExpect(status().isOk())
                .andExpect(jsonPath("$.assignment.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignment.version").value(0));
        assertThat(jdbc.queryForObject("""
                select count(*) from slotwise.assignment where schedule_variant_id = ? and event_id = ?
                """, Integer.class, variant, event)).isZero();

        edit(false, 0, 0, move).andExpect(status().isOk())
                .andExpect(jsonPath("$.variantVersion").value(1))
                .andExpect(jsonPath("$.assignment.version").value(1))
                .andExpect(jsonPath("$.metrics.unassignedEvents").value(0));
        assertThat(startOf(variant)).isEqualTo(Instant.parse(THURSDAY));
        edit(false, 0, 0, move).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VARIANT_VERSION"));
    }

    @Test
    void unassigningAnIncompleteEventUpdatesAllDraftCounters() throws Exception {
        jdbc.update("update slotwise.event set complete = false where id = ?", event);
        jdbc.update("update slotwise.planning_session set input_version = input_version + 1 where id = ?", f.session);
        String unassigned = """
                {"expectedVariantVersion": %d, "expectedAssignmentVersion": %d, "status": "UNASSIGNED",
                 "startsAt": null, "endsAt": null, "roomId": null, "commissionMembers": [], "locked": false}
                """;

        edit(true, 0, 0, unassigned).andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics.unassignedEvents").value(1))
                .andExpect(jsonPath("$.variantVersion").value(0));
        mvc.perform(get("/api/variants/" + variant).cookie(organizer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics.unassignedEvents").value(0));

        edit(false, 0, 0, unassigned).andExpect(status().isOk())
                .andExpect(jsonPath("$.assignment.status").value("UNASSIGNED"))
                .andExpect(jsonPath("$.metrics.unassignedEvents").value(1));
        mvc.perform(get("/api/variants/" + variant).cookie(organizer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics.unassignedEvents").value(1));
        mvc.perform(get(f.path("variants")).cookie(organizer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].metrics.unassignedEvents").value(1));
    }

    private String assigned(String startsAt, String endsAt, boolean locked) {
        return """
                {"expectedVariantVersion": %%d, "expectedAssignmentVersion": %%d, "status": "ASSIGNED",
                 "startsAt": "%s", "endsAt": "%s", "roomId": %d, "commissionMembers": [], "locked": %s}
                """.formatted(startsAt, endsAt, f.room, locked);
    }

    private ResultActions edit(boolean dryRun, long variantVersion, long assignmentVersion, String body) throws Exception {
        return mvc.perform(patch("/api/variants/" + variant + "/assignments/" + event).param("dryRun", String.valueOf(dryRun))
                .cookie(organizer).with(api.xsrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body.formatted(variantVersion, assignmentVersion)));
    }

    private Instant startOf(long variantId) {
        Timestamp start = jdbc.queryForObject("select starts_at from slotwise.assignment where schedule_variant_id = ? "
                + "and event_id = ?", Timestamp.class, variantId, event);
        return start == null ? null : start.toInstant();
    }
}
