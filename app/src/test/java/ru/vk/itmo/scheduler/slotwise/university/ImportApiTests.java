package ru.vk.itmo.scheduler.slotwise.university;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.vk.itmo.scheduler.slotwise.TestcontainersConfiguration;
import ru.vk.itmo.scheduler.slotwise.datagen.DatasetGenerator;
import ru.vk.itmo.scheduler.slotwise.datagen.DatasetProfile;
import ru.vk.itmo.scheduler.slotwise.datagen.UniversityData;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ImportApiTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void previewsAndImportsTheSameSmallDatasetWithoutDuplicates() throws Exception {
        UniversityData data = DatasetGenerator.university(DatasetProfile.S, 42);
        String body = json.writeValueAsString(Map.of(
                "accounts", data.accounts(), "groups", data.groups(), "rooms", data.rooms(),
                "disciplines", data.disciplines(), "teaching", data.teaching(),
                "supervision", data.supervision(), "timetable", data.timetable()));
        long initialAccounts = count("account");
        long initialTimetable = count("timetable_entry");
        long initialGroupMembers = count("study_group_member");
        long initialTeaching = count("teaching");
        long initialSupervision = count("supervision");
        long initialRoomEquipment = count("room_equipment");

        mvc.perform(post("/api/admin/import").with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.applied").value(false))
                .andExpect(jsonPath("$.sections.accounts.created").value(data.accounts().size()));
        assertThat(count("account")).isEqualTo(initialAccounts);
        assertThat(count("timetable_entry")).isEqualTo(initialTimetable);

        var demo = post("/api/admin/demo-data").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"dataset\":\"S\",\"seed\":42}");
        mvc.perform(demo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.applied").value(true))
                .andExpect(jsonPath("$.result.summary.rejected").value(0));
        assertThat(count("account")).isEqualTo(initialAccounts + data.accounts().size());
        assertThat(count("timetable_entry")).isEqualTo(initialTimetable + data.timetable().size());

        String roomWithClasses = data.timetable().stream().map(UniversityData.TimetableEntry::room)
                .filter(Objects::nonNull).findFirst().orElseThrow();
        mvc.perform(applyJson("""
                        {"accounts":[],"groups":[],"rooms":[{"externalId":"%s","online":true,"equipment":[]}],
                         "disciplines":[],"teaching":[],"supervision":[],"timetable":[]}
                        """.formatted(roomWithClasses)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(false))
                .andExpect(jsonPath("$.diagnostics[0].issue.code").value("ROOM_HAS_TIMETABLE"));
        assertThat(count("study_group_member") - initialGroupMembers)
                .isEqualTo(data.groups().stream().mapToLong(g -> g.students().size()).sum());
        assertThat(count("teaching") - initialTeaching).isEqualTo(data.teaching().size());
        assertThat(count("supervision") - initialSupervision).isEqualTo(data.supervision().size());
        assertThat(count("room_equipment") - initialRoomEquipment)
                .isEqualTo(data.rooms().stream().mapToLong(r -> r.equipment().size()).sum());

        mvc.perform(post("/api/admin/import").param("dryRun", "false")
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.created").value(0))
                .andExpect(jsonPath("$.summary.updated").value(0))
                .andExpect(jsonPath("$.summary.rejected").value(0))
                .andExpect(jsonPath("$.sections.timetable.unchanged").value(data.timetable().size()));
        assertThat(count("account")).isEqualTo(initialAccounts + data.accounts().size());
        assertThat(count("timetable_entry")).isEqualTo(initialTimetable + data.timetable().size());
    }

    @Test
    void importsDifferentDemoSeedsIndependentlyAndReusesTheSameSeed() throws Exception {
        long firstSeed = -987;
        long secondSeed = -988;
        var first = DatasetGenerator.university(DatasetProfile.S, firstSeed);
        var second = DatasetGenerator.university(DatasetProfile.S, secondSeed);
        long initialAccounts = count("account");
        long initialTimetable = count("timetable_entry");
        long initialSupervision = count("supervision");

        mvc.perform(demoJson(firstSeed)).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.sections.accounts.created").value(first.accounts().size()));
        mvc.perform(demoJson(secondSeed)).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.sections.accounts.created").value(second.accounts().size()))
                .andExpect(jsonPath("$.result.sections.timetable.created").value(second.timetable().size()))
                .andExpect(jsonPath("$.result.sections.supervision.created").value(second.supervision().size()));

        mvc.perform(demoJson(firstSeed)).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.applied").value(false))
                .andExpect(jsonPath("$.result.summary.unchanged").value(
                        first.accounts().size() + first.groups().size() + first.rooms().size() + first.disciplines().size()
                                + first.teaching().size() + first.supervision().size() + first.timetable().size()));
        assertThat(count("account") - initialAccounts).isEqualTo(first.accounts().size() + second.accounts().size());
        assertThat(count("timetable_entry") - initialTimetable).isEqualTo(first.timetable().size() + second.timetable().size());
        assertThat(count("supervision") - initialSupervision).isEqualTo(first.supervision().size() + second.supervision().size());
    }

    @Test
    void previewsParticipantRoleChangeAndOnlyRevokesLoginSessionsWhenApplied() throws Exception {
        String email = "participant-role-review@example.org";
        String password = "test-participant-password";
        long participant = jdbc.queryForObject("""
                insert into slotwise.account (external_id, email, first_name, last_name, password_hash, role)
                values ('participant-role-review', ?, 'Test', 'Student', ?, 'STUDENT') returning id
                """, Long.class, email, passwordEncoder.encode(password));
        long organizer = jdbc.queryForObject("""
                insert into slotwise.account (email, first_name, last_name, role)
                values ('role-review-owner@example.org', 'Test', 'Owner', 'ORGANIZER') returning id
                """, Long.class);
        long planningSession = jdbc.queryForObject("""
                insert into slotwise.planning_session (organizer_id, name, starts_at, ends_at, collection_starts_at, collection_ends_at)
                values (?, 'Review session', '2027-06-14T06:00:00Z', '2027-06-16T16:00:00Z',
                        '2027-06-10T06:00:00Z', '2027-06-13T16:00:00Z') returning id
                """, Long.class, organizer);
        jdbc.update("insert into slotwise.session_participant (planning_session_id, account_id) values (?, ?)",
                planningSession, participant);
        long event = jdbc.queryForObject("""
                insert into slotwise.event (planning_session_id, event_type_id, title, duration_minutes, format)
                values (?, (select min(id) from slotwise.event_type), 'Review event', 30, 'ONLINE') returning id
                """, Long.class, planningSession);
        jdbc.update("""
                insert into slotwise.event_participant (planning_session_id, event_id, account_id, participant_role)
                values (?, ?, ?, 'STUDENT')
                """, planningSession, event, participant);
        var firstSession = login(email, password, "STUDENT");
        var secondSession = login(email, password, "STUDENT");
        assertThat(firstSession.getValue()).isNotEqualTo(secondSession.getValue());

        String body = """
                {"accounts":[{"externalId":"participant-role-review","email":"%s","firstName":"Test",
                  "lastName":"Student","role":"ORGANIZER","timeZone":"Europe/Moscow"}],
                 "groups":[],"rooms":[],"disciplines":[],"teaching":[],"supervision":[],"timetable":[]}
                """.formatted(email);
        mvc.perform(post("/api/admin/import").with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(false))
                .andExpect(jsonPath("$.summary.updated").value(1))
                .andExpect(jsonPath("$.summary.rejected").value(0))
                .andExpect(jsonPath("$.diagnostics[0].path").value("accounts[0].role"))
                .andExpect(jsonPath("$.diagnostics[0].issue.code").value("PLANNING_PARTICIPANT_ROLE_CHANGED"))
                .andExpect(jsonPath("$.diagnostics[0].issue.severity").value("WARNING"));
        mvc.perform(get("/api/auth/me").cookie(firstSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("STUDENT"));
        assertThat(sessionInputVersion(planningSession)).isZero();

        mvc.perform(applyJson(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(true))
                .andExpect(jsonPath("$.summary.rejected").value(0));
        for (Cookie session : new Cookie[]{firstSession, secondSession}) {
            mvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
        }
        assertThat(sessionInputVersion(planningSession)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select participant_role from slotwise.event_participant where event_id = ? and account_id = ?
                """, String.class, event, participant)).isEqualTo("STUDENT");
        login(email, password, "ORGANIZER");
    }

    @Test
    void appliesValidRowsWithErrorsOnlyAfterExplicitConfirmation() throws Exception {
        String body = """
                {"accounts":[
                  {"externalId":"b4-student","email":"b4-student@example.org","lastName":"Student","firstName":"Test",
                   "role":"STUDENT","timeZone":"Europe/Moscow"},
                  {"externalId":"b4-secret","email":"b4-secret@example.org","lastName":"Secret","firstName":"Test",
                   "role":"STUDENT","timeZone":"Europe/Moscow","passwordHash":"{unsupported}non-public-test-hash"}],
                 "groups":[{"externalId":"B4-BAD","students":["b4-secret"]}],
                 "rooms":[],"disciplines":[],"teaching":[],"supervision":[],"timetable":[]}
                """;
        var apply = post("/api/admin/import").param("dryRun", "false")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body);
        mvc.perform(apply)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(false))
                .andExpect(jsonPath("$.diagnostics[*].path").value(
                        contains("accounts[1].passwordHash", "groups[0].students[0]")))
                .andExpect(content().string(not(containsString("non-public-test-hash"))));
        assertThat(jdbc.queryForObject("select count(*) from slotwise.account where external_id like 'b4-%'", Long.class))
                .isZero();

        mvc.perform(apply.param("allowPartial", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(true))
                .andExpect(jsonPath("$.summary.created").value(1))
                .andExpect(jsonPath("$.summary.rejected").value(2));
        assertThat(jdbc.queryForObject("select count(*) from slotwise.account where external_id like 'b4-%'", Long.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select password_hash from slotwise.account where external_id = 'b4-student'", String.class))
                .isNull();
    }

    @Test
    void updatesAnExistingAccountByExternalIdAndDetectsConflictingEmail() throws Exception {
        String body = """
                {"accounts":[{"externalId":"import-update","email":"import-update@example.org",
                  "lastName":"Before","firstName":"Test","role":"TEACHER","timeZone":"Europe/Moscow"}],
                 "groups":[],"rooms":[],"disciplines":[],"teaching":[],"supervision":[],"timetable":[]}
                """;
        mvc.perform(applyJson(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections.accounts.created").value(1));
        Long id = jdbc.queryForObject("select id from slotwise.account where external_id = 'import-update'", Long.class);

        mvc.perform(applyJson(body.replace("Before", "After")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections.accounts.updated").value(1));
        assertThat(jdbc.queryForObject("select last_name from slotwise.account where id = ?", String.class, id))
                .isEqualTo("After");
        assertThat(jdbc.queryForObject("select version from slotwise.account where id = ?", Long.class, id))
                .isEqualTo(1L);

        mvc.perform(applyJson(body.replace("\"import-update\"", "\"import-conflict\"")
                        .replace("import-update@example.org", "IMPORT-UPDATE@example.org")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(false))
                .andExpect(jsonPath("$.diagnostics[0].issue.code").value("EMAIL_CONFLICT"));
    }

    @Test
    void organizerCannotImportDemoData() throws Exception {
        mvc.perform(post("/api/admin/demo-data").with(user("organizer").roles("ORGANIZER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dataset\":\"S\",\"seed\":42}"))
                .andExpect(status().isForbidden());
    }

    private static MockHttpServletRequestBuilder applyJson(String body) {
        return post("/api/admin/import").param("dryRun", "false")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static MockHttpServletRequestBuilder demoJson(long seed) {
        return post("/api/admin/demo-data").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"dataset":"S","seed":%d}
                        """.formatted(seed));
    }

    private Cookie login(String email, String password, String role) throws Exception {
        return Objects.requireNonNull(mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value(role))
                .andReturn().getResponse().getCookie("SESSION"));
    }

    private long sessionInputVersion(long session) {
        return jdbc.queryForObject("select input_version from slotwise.planning_session where id = ?", Long.class, session);
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from slotwise." + table, Long.class);
    }
}
