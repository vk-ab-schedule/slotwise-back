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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import ru.vk.itmo.scheduler.slotwise.ApiTestSupport;
import ru.vk.itmo.scheduler.slotwise.TestcontainersConfiguration;
import ru.vk.itmo.scheduler.slotwise.domain.Event;
import ru.vk.itmo.scheduler.slotwise.domain.Person;
import ru.vk.itmo.scheduler.slotwise.domain.Score;
import ru.vk.itmo.scheduler.slotwise.domain.solver.ProgressListener;
import ru.vk.itmo.scheduler.slotwise.domain.solver.SolveParams;
import ru.vk.itmo.scheduler.slotwise.planning.PlanningSessionRepository;
import ru.vk.itmo.scheduler.slotwise.solver.greedy.GreedySolver;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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
    @MockitoSpyBean
    ProblemLoader loader;
    @Autowired
    PlatformTransactionManager transactions;
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
    void usesTheGranularitySavedOnTheEvent() {
        long consultation = f.consultation(true);
        jdbc.update("update slotwise.event set granularity = 'PER_GROUP' where id = ?", consultation);

        var problem = loader.load(sessions.findById(f.session).orElseThrow());

        assertThat(problem.event(consultation).scope()).isEqualTo(Event.Scope.GROUP);
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

    @Test
    void ignoresDraftBusyIntervalsUntilTheParticipantSubmits() {
        f.consultation(true);
        f.classes(f.teacher, "2027-01-18T09:00Z", "2027-01-18T10:00Z");
        jdbc.update("""
                insert into slotwise.busy_interval (planning_session_id, account_id, starts_at, ends_at, importance)
                values (?, ?, '2027-01-18T10:00Z', '2027-01-18T11:00Z', 'CRITICAL')
                """, f.session, f.teacher);

        var session = sessions.findById(f.session).orElseThrow();
        assertThat(loader.load(session).person(f.teacher).busy())
                .extracting(Person.Busy::kind).containsExactly(Person.Busy.Kind.TIMETABLE);

        f.submitted(f.teacher);
        assertThat(loader.load(session).person(f.teacher).busy())
                .extracting(Person.Busy::kind).containsExactlyInAnyOrder(
                        Person.Busy.Kind.TIMETABLE, Person.Busy.Kind.CRITICAL);
    }

    @Test
    void workingSlotsFitEntirelyWithinTheSessionPeriod() {
        jdbc.update("""
                update slotwise.planning_session
                set starts_at = '2027-01-18T09:00:00.500Z', ends_at = '2027-01-18T09:58Z'
                where id = ?
                """, f.session);

        var grid = loader.load(sessions.findById(f.session).orElseThrow()).grid();
        assertThat(grid.workingPeriods()).singleElement().satisfies(period -> {
            assertThat(grid.toInstant(period.start())).isEqualTo(Instant.parse("2027-01-18T09:05:00Z"));
            assertThat(grid.toInstant(period.end())).isEqualTo(Instant.parse("2027-01-18T09:55:00Z"));
        });
    }

    @Test
    void eventLongerThanTheSessionIsReportedAndRemainsUnassigned() throws Exception {
        jdbc.update("""
                update slotwise.planning_session
                set starts_at = '2027-01-18T09:00Z', ends_at = '2027-01-18T10:00Z'
                where id = ?
                """, f.session);
        long exam = f.event("Экзамен по дисциплине", 90, true);
        f.participant(exam, f.student, "STUDENT");

        precheck(api.login(f.organizer), 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issues[?(@.code == 'EVENT_LONGER_THAN_WORKING_DAY')].eventIds[*]",
                        contains((int) exam)));

        var problem = loader.load(sessions.findById(f.session).orElseThrow());
        assertThat(problem.events()).extracting(Event::id).containsExactly(exam);
        var result = new GreedySolver().solve(problem, new SolveParams(Duration.ofSeconds(5), 42),
                ProgressListener.NONE);
        assertThat(result.assignments()).isEmpty();
        assertThat(result.score()).contains(new Score(1, 0));
    }

    @Test
    void precheckReadsVersionAndResponsesFromOneSnapshot() throws Exception {
        var concurrentWrite = new TransactionTemplate(transactions);
        concurrentWrite.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        doAnswer(invocation -> {
            concurrentWrite.executeWithoutResult(status -> {
                f.submitted(f.teacher);
                jdbc.update("update slotwise.planning_session set input_version = 1 where id = ?", f.session);
            });
            return invocation.callRealMethod();
        }).when(loader).load(any());

        precheck(api.login(f.organizer), 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inputVersion").value(0))
                .andExpect(jsonPath("$.nonRespondentIds", containsInAnyOrder(
                        (int) f.teacher, (int) f.student, (int) f.otherStudent)));
        assertThat(sessions.findById(f.session).orElseThrow().getInputVersion()).isEqualTo(1L);
    }

    private ResultActions precheck(Cookie session, long version) throws Exception {
        return mvc.perform(post(f.path("precheck")).cookie(session).with(api.xsrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedInputVersion\":" + version + "}"));
    }
}
