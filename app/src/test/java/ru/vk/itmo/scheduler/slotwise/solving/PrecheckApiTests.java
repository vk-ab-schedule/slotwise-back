package ru.vk.itmo.scheduler.slotwise.solving;

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
import ru.vk.itmo.scheduler.slotwise.domain.Event;
import ru.vk.itmo.scheduler.slotwise.domain.Person;
import ru.vk.itmo.scheduler.slotwise.planning.PlanningSessionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PrecheckApiTests {
    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    ProblemLoader loader;
    @Autowired
    PlanningSessionRepository sessions;
    ApiTestSupport api;
    SessionFixture f;

    @BeforeEach
    void fixture() {
        api = new ApiTestSupport(mvc, jdbc, passwordEncoder);
        f = new SessionFixture(api, jdbc);
    }

    @Test
    void loadsCompleteEventsWithTheirPeopleRoomsAndWorkingTime() {
        long consultation = f.consultation(true);
        f.consultation(false);
        f.classes(f.teacher, "2027-01-18T09:00Z", "2027-01-18T10:30Z");

        var problem = loader.load(sessions.findById(f.session).orElseThrow());

        assertThat(problem.events()).extracting(Event::id).containsExactly(consultation);
        assertThat(problem.event(consultation).duration()).isEqualTo(6);
        assertThat(problem.event(consultation).scope()).isEqualTo(Event.Scope.INDIVIDUAL);
        assertThat(problem.person(f.student).role()).isEqualTo(Person.Role.STUDENT);
        assertThat(problem.person(f.teacher).role()).isEqualTo(Person.Role.TEACHER);
        assertThat(problem.person(f.teacher).busy()).singleElement()
                .satisfies(busy -> assertThat(busy.kind()).isEqualTo(Person.Busy.Kind.TIMETABLE))
                .satisfies(busy -> assertThat(busy.range().length()).isEqualTo(18));
        assertThat(problem.rooms()).singleElement().satisfies(room -> assertThat(room.capacity()).isEqualTo(10));
        assertThat(problem.grid().workingPeriods()).hasSize(5)
                .allSatisfy(period -> assertThat(period.length()).isEqualTo(120));
        assertThat(problem.roomChangeBuffer()).isEqualTo(3);
    }

    @Test
    void supervisorCannotSitOnTheCommissionOfTheirStudent() {
        jdbc.update("insert into slotwise.commission_candidate values (?, ?, true)", f.session, f.teacher);
        long defense = f.event("Защита", 30, true);
        f.participant(defense, f.student, "STUDENT");

        var problem = loader.load(sessions.findById(f.session).orElseThrow());

        assertThat(problem.event(defense).commissionExcluded()).containsExactly(f.teacher);
        assertThat(problem.event(defense).commission().size()).isEqualTo(3);
    }

    @Test
    void reportsUnplaceableIncompleteEventsAndMissingResponses() throws Exception {
        f.consultation(true);
        long exam = f.event("Экзамен по дисциплине", 90, true);
        f.participant(exam, f.otherStudent, "STUDENT");
        f.needsEquipment(exam, "Проектор");
        long incomplete = f.consultation(false);
        f.submitted(f.student);

        precheck(api.login(f.organizer), 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(f.session))
                .andExpect(jsonPath("$.inputVersion").value(0))
                .andExpect(jsonPath("$.incompleteData").value(true))
                .andExpect(jsonPath("$.nonRespondentIds", containsInAnyOrder(
                        (int) f.teacher, (int) f.otherStudent)))
                .andExpect(jsonPath("$.issues[?(@.code == 'NO_ROOM')].eventIds[*]", contains((int) exam)))
                .andExpect(jsonPath("$.issues[?(@.code == 'NO_ROOM')].severity", contains("ERROR")))
                .andExpect(jsonPath("$.issues[?(@.code == 'INCOMPLETE_EVENTS')].eventIds[*]", contains((int) incomplete)))
                .andExpect(jsonPath("$.issues[?(@.code == 'NOT_RESPONDED')].severity", contains("WARNING")));
    }

    @Test
    void onlyTheOwnerChecksTheCurrentInputVersion() throws Exception {
        Cookie owner = api.login(f.organizer);

        precheck(owner, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_INPUT_VERSION"));
        precheck(api.login(api.account("ORGANIZER")), 0)
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/solvers").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.available == true)].algorithm", contains("GREEDY", "CP_SAT")))
                .andExpect(jsonPath("$[?(@.supportsBestBound == true)].algorithm", contains("CP_SAT")));
    }

    private ResultActions precheck(Cookie session, long version) throws Exception {
        return mvc.perform(post(f.path("precheck")).cookie(session).with(api.xsrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedInputVersion\":" + version + "}"));
    }
}
