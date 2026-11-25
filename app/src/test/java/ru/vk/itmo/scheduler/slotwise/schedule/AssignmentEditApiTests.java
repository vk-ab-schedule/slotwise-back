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
import ru.vk.itmo.scheduler.slotwise.ApiTestSupport;
import ru.vk.itmo.scheduler.slotwise.TestcontainersConfiguration;
import ru.vk.itmo.scheduler.slotwise.solving.SessionFixture;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
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
