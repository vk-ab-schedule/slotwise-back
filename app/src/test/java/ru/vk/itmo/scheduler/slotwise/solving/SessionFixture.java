package ru.vk.itmo.scheduler.slotwise.solving;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import ru.vk.itmo.scheduler.slotwise.ApiTestSupport;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * A planning session in the database for solving tests: Monday to Friday 18–22 January 2027, 9:00–19:00 UTC,
 * one room for ten people, a teacher who supervises a student, and a second student.
 */
public final class SessionFixture {
    public final long organizer;
    public final long teacher;
    public final long student;
    public final long otherStudent;
    public final long room;
    public final long session;
    private final JdbcTemplate jdbc;

    public SessionFixture(ApiTestSupport api, JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        organizer = api.account("ORGANIZER");
        teacher = api.account("TEACHER");
        student = api.account("STUDENT");
        otherStudent = api.account("STUDENT");
        session = jdbc.queryForObject("""
                insert into slotwise.planning_session(organizer_id, name, starts_at, ends_at, time_zone,
                    collection_starts_at, collection_ends_at)
                values (?, ?, '2027-01-18T00:00Z', '2027-01-23T00:00Z', 'UTC', '2027-01-01T00:00Z', '2027-01-10T00:00Z')
                returning id
                """, Long.class, organizer, "Сессия " + UUID.randomUUID());
        for (int day = 1; day <= 5; day++) {
            jdbc.update("insert into slotwise.session_working_day values (?, ?)", session, day);
        }
        for (long person : new long[]{teacher, student, otherStudent}) {
            jdbc.update("insert into slotwise.session_participant(planning_session_id, account_id) values (?, ?)",
                    session, person);
        }
        jdbc.update("insert into slotwise.supervision values (?, ?)", student, teacher);
        room = jdbc.queryForObject("insert into slotwise.room(name, capacity) values (?, 10) returning id", Long.class,
                "Аудитория " + UUID.randomUUID());
        jdbc.update("insert into slotwise.session_room values (?, ?)", session, room);
    }

    /**
     * A 30-minute consultation of the student with the teacher, its supervisor.
     */
    public long consultation(boolean complete) {
        long event = event("Консультация с научным руководителем", 30, complete);
        participant(event, student, "STUDENT");
        participant(event, teacher, "SUPERVISOR");
        return event;
    }

    public long event(String type, int minutes, boolean complete) {
        return jdbc.queryForObject("""
                insert into slotwise.event(planning_session_id, event_type_id, title, duration_minutes, format,
                    commission_size, requires_chair, preparation_days, complete)
                select ?, id, name, ?, 'ONSITE', commission_size, requires_chair, preparation_days, ?
                from slotwise.event_type where name = ? returning id
                """, Long.class, session, minutes, complete, type);
    }

    public void participant(long event, long person, String role) {
        jdbc.update("insert into slotwise.event_participant values (?, ?, ?, ?)", session, event, person, role);
    }

    public void needsEquipment(long event, String name) {
        long equipment = jdbc.queryForObject("insert into slotwise.equipment(name) values (?) returning id", Long.class,
                name + " " + UUID.randomUUID());
        jdbc.update("insert into slotwise.event_equipment values (?, ?)", event, equipment);
    }

    public void submitted(long person) {
        jdbc.update("""
                update slotwise.session_participant set response_status = 'SUBMITTED', submitted_at = now()
                where planning_session_id = ? and account_id = ?
                """, session, person);
    }

    public void classes(long person, String startsAt, String endsAt) {
        jdbc.update("insert into slotwise.timetable_entry(account_id, starts_at, ends_at) values (?, ?::timestamptz, ?::timestamptz)",
                person, startsAt, endsAt);
    }

    public String path(String suffix) {
        return "/api/sessions/" + session + "/" + suffix;
    }

    /**
     * Runs greedy with this seed on the current input and returns the draft variant it created.
     */
    public long draft(MockMvc mvc, ApiTestSupport api, Cookie organizerSession, long seed) throws Exception {
        long version = jdbc.queryForObject("select input_version from slotwise.planning_session where id = ?",
                Long.class, session);
        String job = mvc.perform(post(path("solve")).cookie(organizerSession).with(api.xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"expectedInputVersion": %d, "algorithm": "GREEDY", "timeLimitSeconds": 1, "seed": %d}
                                """.formatted(version, seed)))
                .andReturn().getResponse().getContentAsString();
        long id = JsonPath.<Number>read(job, "$.id").longValue();
        for (int attempt = 0; attempt < 300; attempt++) {
            String body = mvc.perform(get("/api/jobs/" + id).cookie(organizerSession))
                    .andReturn().getResponse().getContentAsString();
            if ("COMPLETED".equals(JsonPath.read(body, "$.status"))) {
                return JsonPath.<Number>read(body, "$.resultId").longValue();
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Job " + id + " did not complete");
    }
}
