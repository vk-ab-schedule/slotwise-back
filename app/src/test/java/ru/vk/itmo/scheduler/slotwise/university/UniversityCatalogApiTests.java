package ru.vk.itmo.scheduler.slotwise.university;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import ru.vk.itmo.scheduler.slotwise.TestcontainersConfiguration;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
@Sql(statements = """
        insert into slotwise.account (id, email, first_name, last_name, password_hash, role, enabled)
        overriding system value values
            (42001, 'catalog-student@example.org', 'Иван', 'Кузнецов', 'secret', 'STUDENT', true),
            (42002, 'catalog-teacher@example.org', 'Мария', 'Романова', null, 'TEACHER', true),
            (42003, 'catalog-disabled@example.org', 'Илья', 'Кузнецов', null, 'STUDENT', false);
        insert into slotwise.study_group (id, external_id, name, active) overriding system value values
            (42001, 'catalog-group', 'ИТ-42', true), (42002, 'catalog-archived', 'ИТ-43', false);
        insert into slotwise.study_group_member (study_group_id, account_id) values (42001, 42001), (42001, 42003);
        insert into slotwise.discipline (id, name, active) overriding system value values
            (42001, 'Алгоритмы', true), (42002, 'Алгоритмы 2', false);
        insert into slotwise.equipment (id, name) overriding system value values (42001, 'Экран 42'), (42002, 'Камера 42');
        insert into slotwise.room (id, external_id, name, building, capacity, online, active) overriding system value values
            (42001, 'catalog-room', 'Аудитория 42', 'Главный', 30, false, true),
            (42002, 'catalog-online', 'Онлайн 42', null, null, true, true),
            (42003, 'catalog-archived-room', 'Аудитория 43', 'Главный', 20, false, false);
        insert into slotwise.room_equipment (room_id, equipment_id) values (42001, 42001), (42001, 42002);
        insert into slotwise.teaching (id, teacher_id, discipline_id, study_group_id) overriding system value values
            (42001, 42002, 42001, 42001), (42002, 42002, 42002, 42002);
        insert into slotwise.supervision (student_id, supervisor_id) values (42001, 42002), (42003, 42002);
        insert into slotwise.timetable_entry (id, room_id, account_id, starts_at, ends_at, description)
        overriding system value values
            (42001, null, 42002, '2027-06-15T09:00:00Z', '2027-06-15T10:00:00Z', 'Лекция 42'),
            (42002, 42001, null, '2027-06-15T10:00:00Z', '2027-06-15T11:00:00Z', null);
        """)
class UniversityCatalogApiTests {
    @Autowired
    private MockMvc mvc;

    @ParameterizedTest
    @CsvSource({
            "people,42001,role,STUDENT",
            "groups,42001,name,ИТ-42",
            "rooms,42001,externalId,catalog-room",
            "disciplines,42001,name,Алгоритмы",
            "equipment,42001,name,Экран 42",
            "teachings,42001,groupId,42001",
            "supervisions,42001/42002,supervisorId,42002",
            "timetable,42001,description,Лекция 42"
    })
    void organizerReadsExistingRecords(String resource, String id, String field, String value) throws Exception {
        mvc.perform(catalog(resource + "/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$." + field).value(equalTo(value), String.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"people/999999", "groups/999999", "rooms/999999", "disciplines/999999",
            "equipment/999999", "teachings/999999", "supervisions/42001/999999", "timetable/999999"})
    void missingRecordsReturnProblemDetails(String path) throws Exception {
        mvc.perform(catalog(path)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UNIVERSITY_RECORD_NOT_FOUND"));
    }

    @Test
    void peopleSearchPaginatesAndOmitsCredentials() throws Exception {
        mvc.perform(catalog("people").param("search", "  КУЗНЕЦОВ ").param("role", "STUDENT")
                        .param("groupId", "42001").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.items[0].email").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotHaveJsonPath());
        mvc.perform(catalog("people").param("groupId", "42001").param("enabled", "false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(42003));
        mvc.perform(catalog("people").param("search", "Кузнецов").param("page", "2147483647").param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.total").value(2));
        mvc.perform(catalog("people").param("search", "%"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void groupAndRoomFiltersPreserveAllRelatedIds() throws Exception {
        mvc.perform(catalog("groups").param("studentId", "42001").param("search", "catalog-group").param("active", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].studentIds[0]").value(42001))
                .andExpect(jsonPath("$.items[0].studentIds[1]").value(42003));
        mvc.perform(catalog("rooms").param("equipmentId", "42002").param("minCapacity", "25")
                        .param("building", "главный").param("online", "false").param("active", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].equipmentIds[0]").value(42001))
                .andExpect(jsonPath("$.items[0].equipmentIds[1]").value(42002));
        mvc.perform(catalog("rooms").param("online", "true").param("search", "Онлайн 42"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].capacity").value(nullValue()))
                .andExpect(jsonPath("$.items[0].equipmentIds").isEmpty());
    }

    @Test
    void namedCatalogsAndRelationsSupportSearchAndFilters() throws Exception {
        mvc.perform(catalog("disciplines").param("search", "АЛГОРИТМЫ").param("active", "false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(42002));
        mvc.perform(catalog("equipment").param("search", "Камера 42"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(42002));
        mvc.perform(catalog("teachings").param("search", "Романова").param("teacherId", "42002")
                        .param("disciplineId", "42001").param("groupId", "42001"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(42001));
        mvc.perform(catalog("supervisions").param("search", "Иван Кузнецов")
                        .param("studentId", "42001").param("supervisorId", "42002"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].studentId").value(42001));
    }

    @Test
    void timetableReturnsOverlapsAndExcludesTouchingBoundaries() throws Exception {
        mvc.perform(catalog("timetable").param("accountId", "42002").param("search", "Лекция")
                        .param("startsAt", "2027-06-15T09:30:00Z").param("endsAt", "2027-06-15T10:00:00Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].startsAt").value("2027-06-15T09:00:00Z"))
                .andExpect(jsonPath("$.items[0].roomId").value(nullValue()));
        mvc.perform(catalog("timetable").param("roomId", "42001").param("endsAt", "2027-06-15T10:00:00Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2027-06-15T09:00:00Z", "2027-06-15T10:00:00Z"})
    void timetableRejectsAnEmptyOrReversedRangeWithProblemDetails(String endsAt) throws Exception {
        mvc.perform(catalog("timetable").param("startsAt", "2027-06-15T10:00:00Z").param("endsAt", endsAt))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_TIMETABLE_RANGE"));
    }

    @Test
    void readsRequireAnAdministratorOrOrganizer() throws Exception {
        mvc.perform(get("/api/university/groups")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/university/groups").with(user("teacher").roles("TEACHER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/university/groups/42001").with(user("student").roles("STUDENT")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/university/groups/42001").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder catalog(String path) {
        return get("/api/university/" + path).with(user("organizer").roles("ORGANIZER"));
    }
}
