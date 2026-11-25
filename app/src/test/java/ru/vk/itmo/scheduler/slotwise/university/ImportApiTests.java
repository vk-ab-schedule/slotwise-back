package ru.vk.itmo.scheduler.slotwise.university;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Test
    void previewsAndImportsTheSameSmallDatasetWithoutDuplicates() throws Exception {
        UniversityData data = DatasetGenerator.university(DatasetProfile.S, 42);
        String body = json.writeValueAsString(Map.of(
                "accounts", data.accounts(), "groups", data.groups(), "rooms", data.rooms(),
                "disciplines", data.disciplines(), "teaching", data.teaching(),
                "supervision", data.supervision(), "timetable", data.timetable()));
        long initialAccounts = count("account");
        long initialTimetable = count("timetable_entry");

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
        assertThat(count("study_group_member")).isEqualTo(data.groups().stream().mapToLong(g -> g.students().size()).sum());
        assertThat(count("teaching")).isEqualTo(data.teaching().size());
        assertThat(count("supervision")).isEqualTo(data.supervision().size());
        assertThat(count("room_equipment")).isEqualTo(data.rooms().stream().mapToLong(r -> r.equipment().size()).sum());

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

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from slotwise." + table, Long.class);
    }
}
