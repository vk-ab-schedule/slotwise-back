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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import ru.vk.itmo.scheduler.slotwise.ApiTestSupport;
import ru.vk.itmo.scheduler.slotwise.TestcontainersConfiguration;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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

    private ResultActions solve(String algorithm, long version, Long baseVariantId) throws Exception {
        return mvc.perform(post(f.path("solve")).cookie(organizer).with(api.xsrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"expectedInputVersion": %d, "algorithm": "%s", "timeLimitSeconds": 1, "seed": 7, "baseVariantId": %s}
                        """.formatted(version, algorithm, baseVariantId)));
    }

    private static long jobId(ResultActions result) throws Exception {
        return JsonPath.<Number>read(result.andReturn().getResponse().getContentAsString(), "$.id").longValue();
    }

    private String awaitCompletion(long job) throws Exception {
        for (int attempt = 0; attempt < 300; attempt++) {
            String body = mvc.perform(get("/api/jobs/" + job).cookie(organizer)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String state = JsonPath.read(body, "$.status");
            if (!state.equals("QUEUED") && !state.equals("RUNNING")) {
                assertThat(state).as(body).isEqualTo("COMPLETED");
                return body;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Job " + job + " did not finish in 30 seconds");
    }
}
