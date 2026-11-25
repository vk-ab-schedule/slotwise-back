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

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class VariantApiTests {
    private static final String RANGE_FROM = "2027-01-18T00:00:00Z";
    private static final String RANGE_TO = "2027-01-23T00:00:00Z";

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
    void organizerSeesTheDraftWithItsPeopleAndProvenance() throws Exception {
        long consultation = f.consultation(true);
        long variant = f.draft(mvc, api, organizer, 1);

        mvc.perform(get("/api/variants/" + variant).cookie(organizer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.algorithm").value("GREEDY"))
                .andExpect(jsonPath("$.parameters.seed").value(1))
                .andExpect(jsonPath("$.staleData").value(false))
                .andExpect(jsonPath("$.incompleteData").value(true))
                .andExpect(jsonPath("$.nonRespondentIds", containsInAnyOrder(
                        (int) f.teacher, (int) f.student, (int) f.otherStudent)))
                .andExpect(jsonPath("$.metrics.unassignedEvents").value(0))
                .andExpect(jsonPath("$.assignments[0].eventId").value(consultation))
                .andExpect(jsonPath("$.assignments[0].status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignments[0].room.id").value(f.room))
                .andExpect(jsonPath("$.assignments[0].participants[*].role", containsInAnyOrder("STUDENT", "SUPERVISOR")));
        mvc.perform(get(f.path("variants")).cookie(organizer))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(variant));
        mvc.perform(get("/api/variants/" + variant).cookie(api.login(api.account("ORGANIZER"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VARIANT_NOT_FOUND"));
    }

    @Test
    void publishingRequiresConfirmationsAndSupersedesThePreviousPublication() throws Exception {
        f.consultation(true);
        long unplaceable = f.consultation(true);
        f.needsEquipment(unplaceable, "Проектор");
        long first = f.draft(mvc, api, organizer, 1);

        publish(first, 0, false, false).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("UNASSIGNED_NOT_CONFIRMED"));
        jdbc.update("update slotwise.planning_session set input_version = input_version + 1 where id = ?", f.session);
        publish(first, 0, true, false).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("STALE_DATA_NOT_CONFIRMED"));
        publish(first, 0, true, true).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedBy").value(f.organizer))
                .andExpect(jsonPath("$.version").value(1));
        publish(first, 0, true, true).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));

        long second = f.draft(mvc, api, organizer, 2);
        publish(second, 0, true, false).andExpect(status().isOk());
        mvc.perform(get("/api/variants/" + first).cookie(organizer))
                .andExpect(jsonPath("$.status").value("SUPERSEDED"));
        publish(first, 2, true, true).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VARIANT_NOT_DRAFT"));
    }

    @Test
    void participantsAndCommissionMembersSeeTheCurrentPublicationWithChangedEvents() throws Exception {
        long consultation = f.consultation(true);
        long member = api.account("TEACHER");
        jdbc.update("insert into slotwise.session_participant(planning_session_id, account_id) values (?, ?)", f.session, member);
        jdbc.update("insert into slotwise.commission_candidate values (?, ?, true)", f.session, member);
        long first = f.draft(mvc, api, organizer, 1);
        publish(first, 0, false, false).andExpect(status().isOk());
        long second = f.draft(mvc, api, organizer, 2);
        jdbc.update("update slotwise.assignment set starts_at = '2027-01-22T15:00Z', ends_at = '2027-01-22T15:30Z' "
                + "where schedule_variant_id = ?", second);
        jdbc.update("insert into slotwise.assignment_commission_member values (?, ?, ?, ?, true)",
                f.session, second, consultation, member);

        mySchedule(api.login(f.student)).andExpect(jsonPath("$.events[0].variantId").value(first));
        publish(second, 0, false, false).andExpect(status().isOk());

        mySchedule(api.login(f.student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("Europe/Moscow"))
                .andExpect(jsonPath("$.events.length()").value(1))
                .andExpect(jsonPath("$.events[0].variantId").value(second))
                .andExpect(jsonPath("$.events[0].startsAt").value("2027-01-22T15:00:00Z"))
                .andExpect(jsonPath("$.events[0].changed").value(true))
                .andExpect(jsonPath("$.events[0].participants[*].accountId", containsInAnyOrder(
                        (int) f.student, (int) f.teacher)));
        mySchedule(api.login(member))
                .andExpect(jsonPath("$.events[0].eventId").value(consultation))
                .andExpect(jsonPath("$.events[0].commissionMembers[0].chair").value(true));
        mySchedule(api.login(f.otherStudent)).andExpect(jsonPath("$.events.length()").value(0));
    }

    private ResultActions publish(long variant, long version, boolean unassigned, boolean stale) throws Exception {
        return mvc.perform(post("/api/variants/" + variant + "/publish").cookie(organizer).with(api.xsrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"expectedVersion": %d, "confirmUnassigned": %s, "confirmStaleData": %s}
                        """.formatted(version, unassigned, stale)));
    }

    private ResultActions mySchedule(Cookie session) throws Exception {
        return mvc.perform(get("/api/me/schedule").cookie(session).param("from", RANGE_FROM).param("to", RANGE_TO));
    }
}
