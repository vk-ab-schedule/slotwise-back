package ru.vk.itmo.scheduler.slotwise.planning;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import ru.vk.itmo.scheduler.slotwise.TestcontainersConfiguration;
import ru.vk.itmo.scheduler.slotwise.account.AccountRole;
import ru.vk.itmo.scheduler.slotwise.auth.AccountPrincipal;
import ru.vk.itmo.scheduler.slotwise.eventtype.ParticipantRole;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.EventParticipant;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.EventPredecessor;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.EventResponse;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.UpdateEventRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SessionPreparationApiTests {
    private static final String SESSION = """
            {"name":"Планирование B13","startsAt":"2027-04-05T09:00:00Z","endsAt":"2027-04-09T19:00:00Z",
             "timeZone":"UTC","workingDays":["MONDAY","WEDNESDAY"],"workingDayStart":"09:00:00","workingDayEnd":"19:00:00",
             "collectionStartsAt":"2027-04-01T09:00:00Z","collectionEndsAt":"2027-04-05T08:00:00Z"}
            """;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;

    @Test
    void persistsWorkingDaysAndSetsUpParticipantsCommissionAndRooms() throws Exception {
        var fixture = fixture();
        long session = newSession(fixture.owner());
        assertThat(jdbc.queryForList("""
                select day_of_week from slotwise.session_working_day where planning_session_id=? order by day_of_week
                """, Integer.class, session)).containsExactly(1, 3);
        long outsider = account(AccountRole.ORGANIZER);
        mvc.perform(get("/api/sessions").with(authentication(actor(outsider, AccountRole.ORGANIZER))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/sessions/{id}", session).with(authentication(actor(outsider, AccountRole.ORGANIZER))))
                .andExpect(status().isNotFound());

        saveParticipants(fixture, session);
        jdbc.update("""
                update slotwise.session_participant set response_status='SUBMITTED',submitted_at=now(),calendar_revision=1
                where planning_session_id=? and account_id=?
                """, session, fixture.student());

        mvc.perform(put("/api/sessions/{id}/commission-pool", session).with(csrf())
                        .with(authentication(actor(fixture.owner(), AccountRole.ORGANIZER)))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "expectedVersion", 1, "candidates",
                                List.of(Map.of("accountId", fixture.teacher(), "canChair", true))))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.candidates[0].canChair").value(true))
                .andExpect(jsonPath("$.inputVersion").value(2));
        mvc.perform(put("/api/sessions/{id}/rooms", session).with(csrf())
                        .with(authentication(actor(fixture.owner(), AccountRole.ORGANIZER)))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "expectedVersion", 2, "roomIds", List.of(fixture.room())))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roomIds[0]").value(fixture.room()))
                .andExpect(jsonPath("$.inputVersion").value(3));

        var patch = SESSION.replace("{", "{\"expectedVersion\":3,")
                .replace("\"MONDAY\",\"WEDNESDAY\"", "\"TUESDAY\",\"THURSDAY\"");
        mvc.perform(patch("/api/sessions/{id}", session).with(csrf())
                        .with(authentication(actor(fixture.owner(), AccountRole.ORGANIZER)))
                        .contentType(MediaType.APPLICATION_JSON).content(patch))
                .andExpect(status().isOk()).andExpect(jsonPath("$.inputVersion").value(4));
        assertThat(jdbc.queryForList("""
                select day_of_week from slotwise.session_working_day where planning_session_id=? order by day_of_week
                """, Integer.class, session)).containsExactly(2, 4);
        assertThat(jdbc.queryForObject("""
                select response_status from slotwise.session_participant where planning_session_id=? and account_id=?
                """, String.class, session, fixture.student())).isEqualTo("SUBMITTED");
        mvc.perform(patch("/api/sessions/{id}", session).with(csrf())
                        .with(authentication(actor(fixture.owner(), AccountRole.ORGANIZER)))
                        .contentType(MediaType.APPLICATION_JSON).content(patch))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_SESSION_VERSION"));

        mvc.perform(get("/api/sessions").with(authentication(actor(fixture.student(), AccountRole.STUDENT))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(session));
        mvc.perform(get("/api/sessions/{id}/participants", session).with(authentication(actor(outsider, AccountRole.ORGANIZER))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/sessions/{id}/events", session).with(authentication(actor(fixture.student(), AccountRole.STUDENT))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/sessions/{id}/events", session).with(csrf())
                        .with(authentication(actor(outsider, AccountRole.ORGANIZER)))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"expectedVersion":4,"eventTypeId":1,"studyGroupIds":[],"studentIds":[]}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void createsCompleteAndIncompleteEventsAndBackfillsTemplateDependencies() throws Exception {
        var fixture = fixture();
        long session = newSession(fixture.owner());
        saveParticipants(fixture, session);
        var first = body(createEvents(fixture, session, 1, type("Защита"), List.of(fixture.group()), List.of(fixture.student()))
                .andExpect(jsonPath("$.issues[0].code").value("MISSING_SUPERVISOR"))).get("events");
        assertThat(first.size()).isEqualTo(2);
        long defense = first.get(0).get("id").asLong();
        assertThat(first.get(0).get("complete").asBoolean()).isTrue();
        assertThat(first.get(1).get("complete").asBoolean()).isFalse();
        assertThat(first.get(0).get("participants").size()).isEqualTo(2);

        long predecessor = body(createEvents(fixture, session, 2, type("Предзащита"), List.of(), List.of(fixture.student())))
                .get("events").get(0).get("id").asLong();
        mvc.perform(get("/api/sessions/{id}/events/{event}", session, defense)
                        .with(authentication(actor(fixture.owner(), AccountRole.ORGANIZER))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dependencies[0].predecessorEventId").value(predecessor))
                .andExpect(jsonPath("$.dependencies[0].minGapDays").value(3))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/api/sessions/{id}/participants", session)
                        .with(authentication(actor(fixture.owner(), AccountRole.ORGANIZER))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.participants[*].id", hasItem((int) fixture.teacher())));

        var emptySelection = "{\"expectedVersion\":3,\"studyGroupIds\":[],\"accountIds\":[]}";
        mvc.perform(put("/api/sessions/{id}/participants", session).with(csrf())
                        .with(authentication(actor(fixture.owner(), AccountRole.ORGANIZER)))
                        .contentType(MediaType.APPLICATION_JSON).content(emptySelection))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PARTICIPANT_USED_BY_EVENT"));

        var cyclicEdit = json.writeValueAsString(Map.of("expectedVersion", 0, "title", "Предзащита",
                "durationMinutes", 30, "format", "ONSITE", "preparationDays", 0, "commissionSize", 3,
                "requiresChair", true, "equipmentIds", List.of(), "participants",
                List.of(Map.of("accountId", fixture.student(), "role", "STUDENT")),
                "dependencies", List.of(Map.of("predecessorEventId", defense, "minGapDays", 0))));
        mvc.perform(put("/api/sessions/{id}/events/{event}", session, predecessor).with(csrf())
                        .with(authentication(actor(fixture.owner(), AccountRole.ORGANIZER)))
                        .contentType(MediaType.APPLICATION_JSON).content(cyclicEdit))
                .andExpect(status().is(422)).andExpect(jsonPath("$.code").value("CYCLIC_EVENT_DEPENDENCIES"));
        mvc.perform(delete("/api/sessions/{id}/events/{event}", session, defense).param("expectedVersion", "0")
                        .with(csrf()).with(authentication(actor(fixture.owner(), AccountRole.ORGANIZER))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_EVENT_VERSION"));
        mvc.perform(delete("/api/sessions/{id}/events/{event}", session, predecessor).param("expectedVersion", "0")
                        .with(csrf()).with(authentication(actor(fixture.owner(), AccountRole.ORGANIZER))))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/sessions/{id}/events/{event}", session, defense)
                        .with(authentication(actor(fixture.owner(), AccountRole.ORGANIZER))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dependencies").isEmpty())
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void usesTeachingLinksAndExposesMissingParticipantsAndTypeCatalog() throws Exception {
        var f = fixture();
        long session = newSession(f.owner());
        long exam = type("Экзамен по дисциплине");
        var request = Map.of("expectedVersion", 0, "eventTypeId", exam, "studyGroupIds", List.of(f.group()),
                "studentIds", List.of(), "disciplineId", f.discipline());
        mvc.perform(post("/api/sessions/{id}/events", session).with(csrf())
                        .with(authentication(actor(f.owner(), AccountRole.ORGANIZER)))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.events[0].complete").value(true))
                .andExpect(jsonPath("$.events[0].granularity").value("PER_GROUP"))
                .andExpect(jsonPath("$.events[0].participants[*].role", hasItem("DISCIPLINE_TEACHER")));
        createEvents(f, session, 1, exam, List.of(f.group()), List.of())
                .andExpect(jsonPath("$.events[0].complete").value(false))
                .andExpect(jsonPath("$.issues[0].code").value("MISSING_TEACHER"));
        mvc.perform(get("/api/event-types").param("search", "ЭКЗАМЕН")
                        .with(authentication(actor(f.owner(), AccountRole.ORGANIZER))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(exam))
                .andExpect(jsonPath("$.items[0].dependencies[0].maxGapDays").value(2));
    }

    @Test
    void rejectsAWorkingWeekWithoutAnySlotsInTheSession() throws Exception {
        long owner = account(AccountRole.ORGANIZER);
        mvc.perform(post("/api/sessions").with(csrf()).with(authentication(actor(owner, AccountRole.ORGANIZER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SESSION.replace("\"MONDAY\",\"WEDNESDAY\"", "\"SATURDAY\",\"SUNDAY\"")))
                .andExpect(status().is(422)).andExpect(jsonPath("$.code").value("NO_WORKING_TIME"));
    }

    @Test
    void cannotReopenAnExpiredCollectionButCanRenameTheSession() throws Exception {
        long owner = account(AccountRole.ORGANIZER);
        var auth = authentication(actor(owner, AccountRole.ORGANIZER));
        String expired = SESSION.replace("2027-04-01T09:00:00Z", "2020-04-01T09:00:00Z")
                .replace("2027-04-05T08:00:00Z", "2020-04-05T08:00:00Z");
        long session = body(mvc.perform(post("/api/sessions").with(csrf()).with(auth)
                        .contentType(MediaType.APPLICATION_JSON).content(expired))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.collectionStatus").value("CLOSED"))).get("id").asLong();

        String reopen = SESSION.replace("{", "{\"expectedVersion\":0,");
        mvc.perform(patch("/api/sessions/{id}", session).with(csrf()).with(auth)
                        .contentType(MediaType.APPLICATION_JSON).content(reopen))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COLLECTION_CLOSED"));
        mvc.perform(patch("/api/sessions/{id}/collection", session).with(csrf()).with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0,\"endsAt\":\"2027-04-05T08:00:00Z\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COLLECTION_CLOSED"));

        String rename = expired.replace("{", "{\"expectedVersion\":0,").replace("Планирование B13", "Новое название");
        mvc.perform(patch("/api/sessions/{id}", session).with(csrf()).with(auth)
                        .contentType(MediaType.APPLICATION_JSON).content(rename))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Новое название"))
                .andExpect(jsonPath("$.collectionStatus").value("CLOSED"))
                .andExpect(jsonPath("$.collectionEndsAt").value("2020-04-05T08:00:00Z"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void movesOnlyAutomaticDependenciesWhenStudentsChange() throws Exception {
        var f = fixture();
        long session = newSession(f.owner());
        var predefenses = body(createEvents(f, session, 0, type("Предзащита"), List.of(f.group()), List.of())).get("events");
        var predecessorA = json.treeToValue(predefenses.get(0), EventResponse.class);
        var predecessorB = json.treeToValue(predefenses.get(1), EventResponse.class);
        long manual = body(createEvents(f, session, 1, type("Консультация с научным руководителем"), List.of(),
                List.of(f.student()))).get("events").get(0).get("id").asLong();
        var defense = json.treeToValue(body(createEvents(f, session, 2, type("Защита"), List.of(),
                List.of(f.student()))).get("events").get(0), EventResponse.class);
        var explicit = new EventPredecessor(manual, 0, null);
        var withManual = json.treeToValue(body(updateEvent(f, defense, defense.participants(),
                List.of(defense.dependencies().getFirst(), explicit)).andExpect(status().isOk())), EventResponse.class);

        var studentB = new EventParticipant(f.otherStudent(), ParticipantRole.STUDENT);
        var afterSwap = json.treeToValue(body(updateEvent(f, withManual, List.of(studentB), withManual.dependencies())
                .andExpect(status().isOk())), EventResponse.class);
        assertThat(afterSwap.dependencies()).containsExactlyInAnyOrder(new EventPredecessor(predecessorB.id(), 3, null), explicit);
        assertThat(afterSwap.dependencies()).noneMatch(d -> d.predecessorEventId() == predecessorA.id());

        var studentA = new EventParticipant(f.student(), ParticipantRole.STUDENT);
        updateEvent(f, predecessorB, List.of(studentA), predecessorB.dependencies()).andExpect(status().isOk());
        var updatedSuccessor = json.treeToValue(body(mvc.perform(get("/api/sessions/{id}/events/{event}", session, defense.id())
                        .with(authentication(actor(f.owner(), AccountRole.ORGANIZER))))
                .andExpect(status().isOk())), EventResponse.class);
        assertThat(updatedSuccessor.dependencies()).containsExactly(explicit);
        assertThat(updatedSuccessor.version()).isEqualTo(afterSwap.version() + 1);
    }

    @Test
    void rejectsASecondStudentWhenUpdatingAnIndividualEvent() throws Exception {
        var f = fixture();
        long session = newSession(f.owner());
        var event = json.treeToValue(body(createEvents(f, session, 0, type("Защита"), List.of(),
                List.of(f.student()))).get("events").get(0), EventResponse.class);
        var participants = new ArrayList<>(event.participants());
        participants.add(new EventParticipant(f.otherStudent(), ParticipantRole.STUDENT));
        updateEvent(f, event, participants, event.dependencies()).andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("TOO_MANY_EVENT_STUDENTS"));

        mvc.perform(get("/api/sessions/{id}/events/{event}", session, event.id())
                        .with(authentication(actor(f.owner(), AccountRole.ORGANIZER))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.participants.length()").value(2))
                .andExpect(jsonPath("$.version").value(event.version()));
    }

    private ResultActions updateEvent(Fixture f, EventResponse event, List<EventParticipant> participants,
                                      List<EventPredecessor> dependencies) throws Exception {
        var request = new UpdateEventRequest(event.version(), event.title(), event.description(), event.durationMinutes(),
                event.format(), event.preparationDays(), event.commissionSize(), event.requiresChair(), participants,
                event.equipmentIds(), dependencies);
        return mvc.perform(put("/api/sessions/{id}/events/{event}", event.sessionId(), event.id()).with(csrf())
                .with(authentication(actor(f.owner(), AccountRole.ORGANIZER)))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)));
    }

    private long newSession(long owner) throws Exception {
        return body(mvc.perform(post("/api/sessions").with(csrf()).with(authentication(actor(owner, AccountRole.ORGANIZER)))
                        .contentType(MediaType.APPLICATION_JSON).content(SESSION))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.inputVersion").value(0))).get("id").asLong();
    }

    private void saveParticipants(Fixture f, long session) throws Exception {
        mvc.perform(put("/api/sessions/{id}/participants", session).with(csrf())
                        .with(authentication(actor(f.owner(), AccountRole.ORGANIZER)))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "expectedVersion", 0, "studyGroupIds", List.of(f.group()),
                                "accountIds", List.of(f.student())))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.participants.length()").value(2))
                .andExpect(jsonPath("$.inputVersion").value(1));
    }

    private ResultActions createEvents(Fixture f, long session, long expected, long type, List<Long> groups,
                                       List<Long> students) throws Exception {
        return mvc.perform(post("/api/sessions/{id}/events", session).with(csrf())
                        .with(authentication(actor(f.owner(), AccountRole.ORGANIZER)))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "expectedVersion", expected, "eventTypeId", type, "studyGroupIds", groups,
                                "studentIds", students))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.inputVersion").value(expected + 1));
    }

    private JsonNode body(ResultActions action) throws Exception {
        return json.readTree(action.andReturn().getResponse().getContentAsByteArray());
    }

    private long type(String name) {
        return jdbc.queryForObject("select id from slotwise.event_type where name=?", Long.class, name);
    }

    private Fixture fixture() {
        long owner = account(AccountRole.ORGANIZER);
        long student = account(AccountRole.STUDENT);
        long otherStudent = account(AccountRole.STUDENT);
        long teacher = account(AccountRole.TEACHER);
        long group = jdbc.queryForObject("insert into slotwise.study_group(name) values (?) returning id",
                Long.class, "B13-" + UUID.randomUUID());
        long room = jdbc.queryForObject("insert into slotwise.room(name,online) values ('B13',true) returning id", Long.class);
        long discipline = jdbc.queryForObject("insert into slotwise.discipline(name) values ('Математика') returning id", Long.class);
        jdbc.update("insert into slotwise.study_group_member values (?,?),(?,?)", group, student, group, otherStudent);
        jdbc.update("insert into slotwise.supervision values (?,?)", student, teacher);
        jdbc.update("insert into slotwise.teaching(teacher_id,discipline_id,study_group_id) values (?,?,?)", teacher, discipline, group);
        return new Fixture(owner, student, otherStudent, teacher, group, room, discipline);
    }

    private long account(AccountRole role) {
        return jdbc.queryForObject("""
                insert into slotwise.account(email,first_name,last_name,role)
                values (?, 'Planning', 'Test', ?) returning id
                """, Long.class, UUID.randomUUID() + "@example.org", role.name());
    }

    private static UsernamePasswordAuthenticationToken actor(long id, AccountRole role) {
        var principal = mock(AccountPrincipal.class);
        when(principal.getAccountId()).thenReturn(id);
        when(principal.getRole()).thenReturn(role);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                AuthorityUtils.createAuthorityList("ROLE_" + role.name()));
    }

    private record Fixture(long owner, long student, long otherStudent, long teacher, long group, long room, long discipline) {
    }
}
