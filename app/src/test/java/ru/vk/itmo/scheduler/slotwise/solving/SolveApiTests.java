package ru.vk.itmo.scheduler.slotwise.solving;

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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.vk.itmo.scheduler.slotwise.ApiTestSupport;
import ru.vk.itmo.scheduler.slotwise.TestcontainersConfiguration;
import ru.vk.itmo.scheduler.slotwise.domain.solver.SolveResult;
import ru.vk.itmo.scheduler.slotwise.solving.api.dto.SolveRequest;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SolveApiTests {
    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    PlatformTransactionManager transactions;
    @Autowired
    SolvingService solving;
    @MockitoSpyBean
    SolverJobRunner runner;
    @MockitoSpyBean
    Clock clock;
    ApiTestSupport api;
    SessionFixture f;
    Cookie organizer;

    @BeforeEach
    void fixture() throws Exception {
        api = new ApiTestSupport(mvc, jdbc, passwordEncoder);
        f = new SessionFixture(api, jdbc);
        organizer = api.login(f.organizer);
    }

    @Test
    void greedyCreatesAValidatedDraftAndTheSameRequestReturnsTheSameJob() throws Exception {
        long consultation = f.consultation(true);

        long job = jobId(solve("GREEDY", 0, null)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.sessionId").value(f.session))
                .andExpect(jsonPath("$.inputVersion").value(0))
                .andExpect(jsonPath("$.parameters.seed").value(7))
                .andExpect(jsonPath("$.baseVariantId").isEmpty()));
        var done = awaitCompletion(job);

        assertThat(JsonPath.<String>read(done, "$.solveStatus")).isIn("FEASIBLE", "OPTIMAL");
        assertThat(JsonPath.<Integer>read(done, "$.quality.unassignedEvents")).isZero();
        assertThat(JsonPath.<Double>read(done, "$.progress")).isEqualTo(1.0);
        long variant = JsonPath.<Number>read(done, "$.resultId").longValue();
        Map<String, Object> assignment = jdbc.queryForMap("""
                select a.status, a.starts_at, a.room_id, v.status as variant_status, v.metrics ->> 'hardViolations' as violations
                from slotwise.assignment a join slotwise.schedule_variant v on v.id = a.schedule_variant_id
                where a.schedule_variant_id = ? and a.event_id = ?
                """, variant, consultation);
        assertThat(assignment).containsEntry("status", "ASSIGNED").containsEntry("room_id", f.room)
                .containsEntry("variant_status", "DRAFT").containsEntry("violations", "0");
        assertThat(((Timestamp) assignment.get("starts_at")).toInstant())
                .isBetween(Instant.parse("2027-01-18T09:00:00Z"), Instant.parse("2027-01-22T18:30:00Z"));

        solve("GREEDY", 0, null).andExpect(jsonPath("$.id").value(job));
        String snapshot = jdbc.queryForObject("select problem::text from slotwise.input_snapshot "
                + "where planning_session_id = ?", String.class, f.session);
        assertThat(JsonPath.<Integer>read(snapshot, "$.problem.events.length()")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(snapshot, "$.problem.grid.slotCount")).isEqualTo(5 * 24 * 12);
        assertThat(JsonPath.<Boolean>read(snapshot, "$.incompleteData")).isTrue();
    }

    @Test
    void cpSatReportsItsBoundAndLockedAssignmentsStayInTheNextDraft() throws Exception {
        long consultation = f.consultation(true);
        var first = awaitCompletion(jobId(solve("CP_SAT", 0, null)));
        assertThat(JsonPath.<Integer>read(first, "$.quality.bestBound")).isNotNull();
        long variant = JsonPath.<Number>read(first, "$.resultId").longValue();
        jdbc.update("update slotwise.assignment set starts_at = '2027-01-21T15:00Z', ends_at = '2027-01-21T15:30Z', "
                + "locked = true where schedule_variant_id = ?", variant);

        var second = awaitCompletion(jobId(solve("GREEDY", 0, variant)
                .andExpect(jsonPath("$.baseVariantId").value(variant))));

        Map<String, Object> kept = jdbc.queryForMap("select starts_at, locked from slotwise.assignment "
                + "where schedule_variant_id = ? and event_id = ?", JsonPath.<Number>read(second, "$.resultId").longValue(),
                consultation);
        assertThat(((Timestamp) kept.get("starts_at")).toInstant()).isEqualTo(Instant.parse("2027-01-21T15:00:00Z"));
        assertThat(kept).containsEntry("locked", true);
    }

    @Test
    void anEventWithoutAFittingRoomStaysUnassignedWithItsPrecheckExplanation() throws Exception {
        long consultation = f.consultation(true);
        f.needsEquipment(consultation, "Проектор");

        var done = awaitCompletion(jobId(solve("GREEDY", 0, null)));

        assertThat(JsonPath.<Integer>read(done, "$.quality.unassignedEvents")).isEqualTo(1);
        String explanation = jdbc.queryForObject("select explanation::text from slotwise.assignment "
                + "where schedule_variant_id = ? and status = 'UNASSIGNED'", String.class,
                JsonPath.<Number>read(done, "$.resultId").longValue());
        assertThat(explanation).contains("NO_ROOM");
    }

    @Test
    void rejectsStaleInputUnavailableSolversAndForeignJobs() throws Exception {
        f.consultation(true);

        solve("GREEDY", 1, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_INPUT_VERSION"));
        solve("TIMEFOLD", 0, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SOLVER_UNAVAILABLE"));
        solve("GREEDY", 0, Long.MAX_VALUE).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VARIANT_NOT_FOUND"));
        long job = jobId(solve("GREEDY", 0, null));
        mvc.perform(get("/api/jobs/" + job).cookie(api.login(api.account("ORGANIZER"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"));
        awaitCompletion(job);
    }

    @Test
    void returnsTheActiveJobBeforeAnOlderMatchingJobAndQueuesOtherSessions() throws Exception {
        f.consultation(true);
        var second = new SessionFixture(api, jdbc);
        second.consultation(true);
        Cookie secondOrganizer = api.login(second.organizer);
        long completed = jobId(solve("GREEDY", 0, null));
        awaitCompletion(completed);

        var running = new CountDownLatch(1);
        var proceed = new CountDownLatch(1);
        doAnswer(invocation -> {
            running.countDown();
            if (!proceed.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("The test did not release the worker");
            }
            return invocation.callRealMethod();
        }).when(runner).solve(argThat(run -> run.sessionId() == f.session && run.params().seed() == 8));

        long active = jobId(solve(f, organizer, "GREEDY", 0, null, 8));
        long queued;
        try {
            assertThat(running.await(10, TimeUnit.SECONDS)).isTrue();
            poll(active).andExpect(jsonPath("$.status").value("RUNNING"))
                    .andExpect(jsonPath("$.progress").isNumber());
            solve("GREEDY", 0, null)
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.id").value(active));
            queued = jobId(solve(second, secondOrganizer, "GREEDY", 0, null, 7)
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("QUEUED"))
                    .andExpect(jsonPath("$.queuePosition").value(1)));
            mvc.perform(get("/api/jobs/" + queued).cookie(secondOrganizer))
                    .andExpect(jsonPath("$.status").value("QUEUED"));
        } finally {
            proceed.countDown();
        }
        awaitCompletion(active);
        awaitCompletion(queued, secondOrganizer, "COMPLETED");
        solve("GREEDY", 0, null).andExpect(jsonPath("$.id").value(completed));
    }

    @Test
    void pollsWhileTheWorkerIsStillUpdatingJobStatus() throws Exception {
        f.consultation(true);
        var updatingStatus = new CountDownLatch(1);
        var finishUpdate = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (Thread.currentThread().getName().startsWith("solver-")) {
                updatingStatus.countDown();
                assertThat(finishUpdate.await(15, TimeUnit.SECONDS)).isTrue();
            }
            return invocation.callRealMethod();
        }).when(clock).instant();

        long job = solving.enqueue(f.session, f.organizer, new SolveRequest(0L, SolverAlgorithm.GREEDY, 1, 7L, null));
        try (var callers = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                assertThat(updatingStatus.await(10, TimeUnit.SECONDS)).isTrue();
                var response = callers.submit(() -> poll(job).andExpect(jsonPath("$.status").value("QUEUED")));
                response.get(5, TimeUnit.SECONDS);
            } finally {
                finishUpdate.countDown();
            }
        }
        awaitCompletion(job);
    }

    @Test
    void queuePositionsMatchExecutionWhenTransactionsCommitInReverseIdOrder() throws Exception {
        f.consultation(true);
        var earlierSession = new SessionFixture(api, jdbc);
        var laterSession = new SessionFixture(api, jdbc);
        earlierSession.consultation(true);
        laterSession.consultation(true);
        Cookie earlierOwner = api.login(earlierSession.organizer);
        Cookie laterOwner = api.login(laterSession.organizer);
        var workerStarted = new CountDownLatch(1);
        var releaseWorker = new CountDownLatch(1);
        var commitEarlier = new CountDownLatch(1);
        var earlierId = new CompletableFuture<Long>();
        var executionOrder = new LinkedBlockingQueue<Long>();

        doAnswer(invocation -> {
            SolverJobRunner.Run run = invocation.getArgument(0);
            if (run.sessionId() == f.session) {
                workerStarted.countDown();
                assertThat(releaseWorker.await(15, TimeUnit.SECONDS)).isTrue();
            } else {
                executionOrder.add(run.jobId());
            }
            return invocation.callRealMethod();
        }).when(runner).solve(argThat(run -> run != null && (
                run.sessionId() == f.session || run.sessionId() == earlierSession.session
                        || run.sessionId() == laterSession.session)));

        long blocker = jobId(solve("GREEDY", 0, null));
        try (var callers = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                assertThat(workerStarted.await(10, TimeUnit.SECONDS)).isTrue();
                var earlier = callers.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                    try {
                        long id = jobId(solve(earlierSession, earlierOwner, "GREEDY", 0, null, 7)
                                .andExpect(status().isAccepted()));
                        earlierId.complete(id);
                        assertThat(commitEarlier.await(10, TimeUnit.SECONDS)).isTrue();
                    } catch (Exception e) {
                        earlierId.completeExceptionally(e);
                        throw new IllegalStateException(e);
                    }
                }));
                long smallerId = earlierId.get(10, TimeUnit.SECONDS);
                long largerId = jobId(solve(laterSession, laterOwner, "GREEDY", 0, null, 7)
                        .andExpect(status().isAccepted())
                        .andExpect(jsonPath("$.queuePosition").value(1)));
                assertThat(largerId).isGreaterThan(smallerId);
                commitEarlier.countDown();
                earlier.get(10, TimeUnit.SECONDS);

                mvc.perform(get("/api/jobs/" + largerId).cookie(laterOwner))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.queuePosition").value(1));
                mvc.perform(get("/api/jobs/" + smallerId).cookie(earlierOwner))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.queuePosition").value(2));

                releaseWorker.countDown();
                assertThat(executionOrder.poll(10, TimeUnit.SECONDS)).isEqualTo(largerId);
                assertThat(executionOrder.poll(10, TimeUnit.SECONDS)).isEqualTo(smallerId);
                awaitCompletion(largerId, laterOwner, "COMPLETED");
                awaitCompletion(smallerId, earlierOwner, "COMPLETED");
            } finally {
                commitEarlier.countDown();
                releaseWorker.countDown();
            }
        }
        awaitCompletion(blocker);
    }

    @Test
    void changingEventDurationConflictsWithTheEntireLockedInterval() throws Exception {
        long event = f.consultation(true);
        long variant = resultId(awaitCompletion(jobId(solve("GREEDY", 0, null))));
        jdbc.update("""
                update slotwise.assignment set starts_at = '2027-01-21T10:00Z', ends_at = '2027-01-21T10:30Z',
                    locked = true where schedule_variant_id = ?
                """, variant);
        jdbc.update("update slotwise.event set duration_minutes = 45 where id = ?", event);
        jdbc.update("update slotwise.planning_session set input_version = 1 where id = ?", f.session);

        solve("GREEDY", 1, variant).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PINNED_CONFLICT"))
                .andExpect(jsonPath("$.issues[0].eventIds[0]").value(event));
        var assignment = jdbc.queryForMap("""
                select locked, ends_at from slotwise.assignment where schedule_variant_id = ? and event_id = ?
                """, variant, event);
        assertThat(assignment).containsEntry("locked", true);
        assertThat(((Timestamp) assignment.get("ends_at")).toInstant()).isEqualTo(Instant.parse("2027-01-21T10:30:00Z"));
    }

    @Test
    void reportsAnOutOfPeriodLockInsteadOfSilentlyMovingTheEvent() throws Exception {
        long event = f.consultation(true);
        long variant = resultId(awaitCompletion(jobId(solve("GREEDY", 0, null))));
        jdbc.update("""
                update slotwise.assignment set starts_at = '2027-01-21T15:00Z', ends_at = '2027-01-21T15:30Z',
                    locked = true where schedule_variant_id = ?
                """, variant);
        jdbc.update("update slotwise.planning_session set ends_at = '2027-01-20T00:00Z', input_version = 1 where id = ?",
                f.session);

        solve("GREEDY", 1, variant).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PINNED_CONFLICT"))
                .andExpect(jsonPath("$.issues[0].eventIds[0]").value(event));
        assertThat(jdbc.queryForObject("select count(*) from slotwise.solver_job where planning_session_id = ?",
                Integer.class, f.session)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select locked from slotwise.assignment where schedule_variant_id = ?",
                Boolean.class, variant)).isTrue();
    }

    @Test
    void editedBaseVariantStartsANewCalculationAtTheSameInputVersion() throws Exception {
        long event = f.consultation(true);
        long base = resultId(awaitCompletion(jobId(solve("GREEDY", 0, null))));
        long beforeEdit = jobId(solve("GREEDY", 0, base));
        awaitCompletion(beforeEdit);
        solve("GREEDY", 0, base).andExpect(jsonPath("$.id").value(beforeEdit));
        jdbc.update("""
                update slotwise.assignment set starts_at = '2027-01-21T15:00Z', ends_at = '2027-01-21T15:30Z',
                    locked = true where schedule_variant_id = ?
                """, base);
        jdbc.update("update slotwise.schedule_variant set version = version + 1 where id = ?", base);

        long afterEdit = jobId(solve("GREEDY", 0, base).andExpect(jsonPath("$.inputVersion").value(0)));
        assertThat(afterEdit).isNotEqualTo(beforeEdit);
        long result = resultId(awaitCompletion(afterEdit));
        Timestamp start = jdbc.queryForObject("""
                select starts_at from slotwise.assignment where schedule_variant_id = ? and event_id = ? and locked
                """, Timestamp.class, result, event);
        assertThat(start.toInstant()).isEqualTo(Instant.parse("2027-01-21T15:00:00Z"));
    }

    @Test
    void recordsCollectionStateForEachNewJobOnTheSameInputSnapshot() throws Exception {
        f.consultation(true);
        f.submitted(f.teacher);
        f.submitted(f.student);
        f.submitted(f.otherStudent);
        doReturn(Instant.parse("2027-01-09T23:59:59Z")).when(clock).instant();
        long first = jobId(solve("GREEDY", 0, null));
        awaitCompletion(first);
        doReturn(Instant.parse("2027-01-10T00:00:01Z")).when(clock).instant();

        long second = jobId(solve(f, organizer, "GREEDY", 0, null, 8));
        awaitCompletion(second);
        var rows = jdbc.queryForList("""
                select input_snapshot_id, incomplete_data from slotwise.solver_job
                where planning_session_id = ? order by id
                """, f.session);
        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst()).containsEntry("incomplete_data", true);
        assertThat(rows.getLast()).containsEntry("incomplete_data", false)
                .containsEntry("input_snapshot_id", rows.getFirst().get("input_snapshot_id"));
    }

    @Test
    void pollingKeepsTheOriginalOutcomeAfterTheDraftIsEdited() throws Exception {
        f.consultation(true);
        long job = jobId(solve("CP_SAT", 0, null));
        String original = awaitCompletion(job);
        long variant = resultId(original);
        jdbc.update("""
                update slotwise.schedule_variant set objective = 999, solve_status = 'FEASIBLE', best_bound = null,
                    metrics = '{"unassignedEvents": 1}'::jsonb, version = version + 1 where id = ?
                """, variant);

        String polled = poll(job).andReturn().getResponse().getContentAsString();
        assertThat(resultId(polled)).isEqualTo(variant);
        assertThat(JsonPath.<String>read(polled, "$.solveStatus"))
                .isEqualTo(JsonPath.<String>read(original, "$.solveStatus"));
        assertThat(JsonPath.<Map<String, Object>>read(polled, "$.quality"))
                .isEqualTo(JsonPath.<Map<String, Object>>read(original, "$.quality"));
    }

    @Test
    void distinguishesNoSolutionFromFailureAndAllowsRetryAfterFailure() throws Exception {
        f.consultation(true);
        doReturn(SolveResult.noSolution(Duration.ZERO)).when(runner)
                .solve(argThat(run -> run != null && run.params().seed() == 8));
        doThrow(new IllegalStateException("Technical diagnostic"))
                .doCallRealMethod().when(runner).solve(argThat(run -> run != null && run.params().seed() == 9));

        long empty = jobId(solve(f, organizer, "GREEDY", 0, null, 8));
        awaitCompletion(empty);
        poll(empty).andExpect(jsonPath("$.solveStatus").value("NO_SOLUTION"))
                .andExpect(jsonPath("$.error").isEmpty())
                .andExpect(jsonPath("$.resultId").isEmpty());
        long failed = jobId(solve(f, organizer, "GREEDY", 0, null, 9));
        awaitCompletion(failed, organizer, "FAILED");
        poll(failed).andExpect(jsonPath("$.error.code").value("SOLVER_FAILED"))
                .andExpect(jsonPath("$.solveStatus").isEmpty())
                .andExpect(jsonPath("$.resultId").isEmpty());
        long retried = jobId(solve(f, organizer, "GREEDY", 0, null, 9));
        assertThat(retried).isNotEqualTo(failed);
        awaitCompletion(retried);
    }

    @Test
    void persistsAnEventLongerThanTheSessionAsUnassigned() throws Exception {
        jdbc.update("""
                update slotwise.planning_session
                set starts_at = '2027-01-18T09:00Z', ends_at = '2027-01-18T10:00Z' where id = ?
                """, f.session);
        long event = f.event("Экзамен по дисциплине", 90, true);
        f.participant(event, f.student, "STUDENT");
        long variant = resultId(awaitCompletion(jobId(solve("GREEDY", 0, null))));

        assertThat(jdbc.queryForObject("""
                select explanation::text from slotwise.assignment
                where schedule_variant_id = ? and event_id = ? and status = 'UNASSIGNED'
                """, String.class, variant, event)).contains("EVENT_LONGER_THAN_WORKING_DAY");
    }

    private ResultActions solve(String algorithm, long version, Long baseVariantId) throws Exception {
        return solve(f, organizer, algorithm, version, baseVariantId, 7);
    }

    private ResultActions solve(SessionFixture fixture, Cookie owner, String algorithm, long version,
                                Long baseVariantId, long seed) throws Exception {
        return mvc.perform(post(fixture.path("solve")).cookie(owner).with(api.xsrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"expectedInputVersion": %d, "algorithm": "%s", "timeLimitSeconds": 1, "seed": %d, "baseVariantId": %s}
                        """.formatted(version, algorithm, seed, baseVariantId)));
    }

    private static long jobId(ResultActions result) throws Exception {
        return JsonPath.<Number>read(result.andReturn().getResponse().getContentAsString(), "$.id").longValue();
    }

    private static long resultId(String completed) {
        return JsonPath.<Number>read(completed, "$.resultId").longValue();
    }

    private ResultActions poll(long job) throws Exception {
        return mvc.perform(get("/api/jobs/" + job).cookie(organizer)).andExpect(status().isOk());
    }

    private String awaitCompletion(long job) throws Exception {
        return awaitCompletion(job, organizer, "COMPLETED");
    }

    private String awaitCompletion(long job, Cookie owner, String expectedStatus) throws Exception {
        for (int attempt = 0; attempt < 300; attempt++) {
            String body = mvc.perform(get("/api/jobs/" + job).cookie(owner)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String state = JsonPath.read(body, "$.status");
            if (!state.equals("QUEUED") && !state.equals("RUNNING")) {
                assertThat(state).as(body).isEqualTo(expectedStatus);
                return body;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Job " + job + " did not finish in 30 seconds");
    }
}
