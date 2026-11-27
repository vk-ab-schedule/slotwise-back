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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import ru.vk.itmo.scheduler.slotwise.ApiTestSupport;
import ru.vk.itmo.scheduler.slotwise.TestcontainersConfiguration;
import ru.vk.itmo.scheduler.slotwise.solving.SessionFixture;

import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
    void variantUsesTheCollectionStateOfItsJobEvenWhenTheInputSnapshotIsReused() throws Exception {
        f.consultation(true);
        f.submitted(f.teacher);
        f.submitted(f.student);
        f.submitted(f.otherStudent);
        doReturn(Instant.parse("2027-01-09T23:59:59Z")).when(clock).instant();
        long first = f.draft(mvc, api, organizer, 1);
        long snapshot = jdbc.queryForObject("select input_snapshot_id from slotwise.schedule_variant where id = ?",
                Long.class, first);

        doReturn(Instant.parse("2027-01-10T00:00:01Z")).when(clock).instant();
        long second = f.draft(mvc, api, organizer, 2);
        mvc.perform(get("/api/variants/" + first).cookie(organizer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.incompleteData").value(true));
        mvc.perform(get("/api/variants/" + second).cookie(organizer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inputSnapshotId").value(snapshot))
                .andExpect(jsonPath("$.incompleteData").value(false));
        mvc.perform(get(f.path("variants")).cookie(organizer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == " + first + ")].incompleteData", contains(true)))
                .andExpect(jsonPath("$.items[?(@.id == " + second + ")].incompleteData", contains(false)));
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
        long member = commissionMember();
        jdbc.update("update slotwise.event set commission_size = 1, requires_chair = true where id = ?", consultation);
        long first = f.draft(mvc, api, organizer, 1);
        atFixedTime(first, 30);
        publish(first, 0, false, false).andExpect(status().isOk());
        long second = f.draft(mvc, api, organizer, 2);
        jdbc.update("update slotwise.assignment set starts_at = '2027-01-22T15:00Z', ends_at = '2027-01-22T15:30Z' "
                + "where schedule_variant_id = ?", second);

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

    @Test
    void staleConfirmationCannotOverrideNewHardViolations() throws Exception {
        f.consultation(true);
        long variant = f.draft(mvc, api, organizer, 1);
        String start = jdbc.queryForObject("select starts_at::text from slotwise.assignment where schedule_variant_id = ?",
                String.class, variant);
        String end = jdbc.queryForObject("select ends_at::text from slotwise.assignment where schedule_variant_id = ?",
                String.class, variant);
        f.classes(f.student, start, end);
        changeInput();

        publish(variant, 0, true, true).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("HARD_VIOLATIONS"))
                .andExpect(jsonPath("$.issues").isNotEmpty());
        mvc.perform(get("/api/variants/" + variant).cookie(organizer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void editingAPlanningEventDoesNotChangeThePublishedPersonalSchedule() throws Exception {
        long event = f.consultation(true);
        long variant = f.draft(mvc, api, organizer, 1);
        publish(variant, 0, false, false).andExpect(status().isOk());
        var before = mySchedule(api.login(f.student)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        mvc.perform(put(f.path("events/" + event)).cookie(organizer).with(api.xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"expectedVersion":0,"title":"Новый заголовок","durationMinutes":30,"format":"ONLINE",
                                 "preparationDays":0,"commissionSize":0,"requiresChair":false,"equipmentIds":[],
                                 "participants":[{"accountId":%d,"role":"STUDENT"},{"accountId":%d,"role":"SUPERVISOR"}],
                                 "dependencies":[]}
                                """.formatted(f.otherStudent, f.teacher)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Новый заголовок"));

        mySchedule(api.login(f.otherStudent)).andExpect(status().isOk()).andExpect(jsonPath("$.events").isEmpty());
        mySchedule(api.login(f.student)).andExpect(status().isOk())
                .andExpect(content().json(before));
        mvc.perform(get("/api/variants/" + variant).cookie(organizer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignments[0].title").value("Консультация с научным руководителем"));
    }

    @Test
    void marksANewPlacementAfterAPublicationWithOnlyUnassignedEvents() throws Exception {
        long event = f.consultation(true);
        f.needsEquipment(event, "Проектор");
        long first = f.draft(mvc, api, organizer, 1);
        publish(first, 0, true, false).andExpect(status().isOk());
        mySchedule(api.login(f.student)).andExpect(jsonPath("$.events").isEmpty());

        jdbc.update("delete from slotwise.event_equipment where event_id = ?", event);
        changeInput();
        long second = f.draft(mvc, api, organizer, 2);
        publish(second, 0, false, false).andExpect(status().isOk());
        mySchedule(api.login(f.student)).andExpect(jsonPath("$.events[0].changed").value(true));
    }

    @Test
    void marksAnEndTimeChangeEvenWhenTheAssignmentStartsInTheSameSlot() throws Exception {
        long event = f.consultation(true);
        long first = f.draft(mvc, api, organizer, 1);
        atFixedTime(first, 30);
        publish(first, 0, false, false).andExpect(status().isOk());
        mySchedule(api.login(f.student)).andExpect(jsonPath("$.events[0].changed").value(false));

        jdbc.update("update slotwise.event set duration_minutes = 35 where id = ?", event);
        changeInput();
        long second = f.draft(mvc, api, organizer, 2);
        atFixedTime(second, 35);
        publish(second, 0, false, false).andExpect(status().isOk());
        mySchedule(api.login(f.student))
                .andExpect(jsonPath("$.events[0].startsAt").value("2027-01-22T14:00:00Z"))
                .andExpect(jsonPath("$.events[0].endsAt").value("2027-01-22T14:35:00Z"))
                .andExpect(jsonPath("$.events[0].changed").value(true));
    }

    @Test
    void marksAChairChangeWithTheSameCommissionMembers() throws Exception {
        long event = f.consultation(true);
        long firstChair = commissionMember();
        long secondChair = commissionMember();
        jdbc.update("update slotwise.event set commission_size = 2, requires_chair = true where id = ?", event);
        long first = f.draft(mvc, api, organizer, 1);
        atFixedTime(first, 30);
        chair(first, firstChair);
        publish(first, 0, false, false).andExpect(status().isOk());

        long second = f.draft(mvc, api, organizer, 2);
        atFixedTime(second, 30);
        chair(second, secondChair);
        publish(second, 0, false, false).andExpect(status().isOk());
        mySchedule(api.login(f.student))
                .andExpect(jsonPath("$.events[0].commissionMembers[0].accountId").value(secondChair))
                .andExpect(jsonPath("$.events[0].changed").value(true));
    }

    @Test
    void showsEventsAddedAfterCalculationBeforeAndAfterPublishingTheStaleDraft() throws Exception {
        f.consultation(true);
        long variant = f.draft(mvc, api, organizer, 1);
        long added = f.consultation(true);
        changeInput();
        String newAssignment = "$.assignments[?(@.eventId == " + added + ")]";

        mvc.perform(get(f.path("variants")).cookie(organizer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(variant))
                .andExpect(jsonPath("$.items[0].version").value(0))
                .andExpect(jsonPath("$.items[0].metrics.unassignedEvents").value(1));
        mvc.perform(get("/api/variants/" + variant).cookie(organizer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics.unassignedEvents").value(1))
                .andExpect(jsonPath(newAssignment + ".status", contains("UNASSIGNED")));
        publish(variant, 0, false, true).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("UNASSIGNED_NOT_CONFIRMED"))
                .andExpect(jsonPath("$.issues[0].eventIds[0]").value(added));
        assertThat(jdbc.queryForObject("""
                select count(*) from slotwise.assignment where schedule_variant_id = ? and event_id = ?
                """, Integer.class, variant, added)).isZero();

        publish(variant, 0, true, true).andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics.unassignedEvents").value(1))
                .andExpect(jsonPath(newAssignment + ".status", contains("UNASSIGNED")));
        long later = f.consultation(true);
        changeInput();
        publish(variant, 0, true, true).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.assignments[?(@.eventId == " + later + ")]").isEmpty());
        mvc.perform(get("/api/variants/" + variant).cookie(organizer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics.unassignedEvents").value(1))
                .andExpect(jsonPath(newAssignment + ".status", contains("UNASSIGNED")));
        mySchedule(api.login(f.student)).andExpect(jsonPath("$.events.length()").value(1));
    }

    private long commissionMember() {
        long member = api.account("TEACHER");
        jdbc.update("insert into slotwise.session_participant(planning_session_id, account_id) values (?, ?)", f.session, member);
        jdbc.update("insert into slotwise.commission_candidate values (?, ?, true)", f.session, member);
        return member;
    }

    private void changeInput() {
        jdbc.update("update slotwise.planning_session set input_version = input_version + 1 where id = ?", f.session);
    }

    private void atFixedTime(long variant, int minutes) {
        jdbc.update("""
                update slotwise.assignment set starts_at = '2027-01-22T14:00Z',
                    ends_at = '2027-01-22T14:00Z'::timestamptz + ? * interval '1 minute'
                where schedule_variant_id = ?
                """, minutes, variant);
    }

    private void chair(long variant, long id) {
        jdbc.update("update slotwise.assignment_commission_member set chair = account_id = ? where schedule_variant_id = ?",
                id, variant);
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
