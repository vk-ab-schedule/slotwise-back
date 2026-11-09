package ru.vk.itmo.scheduler.slotwise.api;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UniversityContractTests extends ApiContractTestBase {
    private static final List<String> CATALOGS = List.of("people", "groups", "rooms", "disciplines",
            "equipment", "teachings", "supervisions", "timetable");
    private static final String TIMETABLE = """
            {"accountId":1,"startsAt":"2026-06-15T06:50:00Z","endsAt":"2026-06-15T08:20:00Z",
             "description":"Lecture"}
            """;
    private static final String IMPORT = """
            {
              "accounts":[{"externalId":"s-1001","email":"ivanov@example.org","lastName":"Иванов",
                "firstName":"Иван","patronymic":"Петрович","role":"STUDENT","timeZone":"Europe/Moscow"},
                {"externalId":"t-17","email":"teacher@example.org","lastName":"Петров",
                "firstName":"Пётр","role":"TEACHER","timeZone":"Europe/Moscow",
                "passwordHash":"{bcrypt}$2a$10$contract-secret"}],
              "groups":[{"externalId":"P3410","students":["s-1001"]}],
              "rooms":[{"externalId":"1404","building":"Кронверкский 49","capacity":30,"equipment":["projector"]}],
              "disciplines":[{"externalId":"db","name":"Базы данных"}],
              "teaching":[{"teacher":"t-17","discipline":"db","group":"P3410"}],
              "supervision":[{"student":"s-1001","supervisor":"t-17"}],
              "timetable":[{"account":"s-1001","start":"2026-06-15T09:50:00+03:00",
                            "end":"2026-06-15T11:20:00+03:00"},
                           {"room":"1404","start":"2026-06-15T09:50:00+03:00",
                            "end":"2026-06-15T11:20:00+03:00"}]
            }
            """;

    @ParameterizedTest
    @MethodSource("catalogPaths")
    void catalogPaginationAcceptsItsBoundariesAndReachesTheStub(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isNotImplemented());
        mvc.perform(get(path).param("page", "0").param("size", "1").param("search", "Иван"))
                .andExpect(status().isNotImplemented());
        mvc.perform(get(path).param("page", "1").param("size", "100"))
                .andExpect(status().isNotImplemented());
    }

    @ParameterizedTest
    @MethodSource("invalidCatalogQueries")
    void invalidPaginationIsRejectedBeforeTheStub(String path, String parameter, String value) throws Exception {
        mvc.perform(get(path).param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @ParameterizedTest
    @MethodSource("validMutations")
    void allValidMutationsReturn501RatherThanPretendingToPersist(String method, String path, String body)
            throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotImplemented())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_IMPLEMENTED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"people", "groups", "rooms", "disciplines", "equipment", "teachings", "timetable"})
    void catalogDetailRejectsNonPositiveIds(String resource) throws Exception {
        mvc.perform(get("/api/university/" + resource + "/1")).andExpect(status().isNotImplemented());
        mvc.perform(get("/api/university/" + resource + "/0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/university/" + resource + "/-1")).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void bodyConstraintsRejectInvalidFields(String method, String path, String body) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @ParameterizedTest
    @ValueSource(strings = {"groups", "rooms", "disciplines"})
    void versionedDeletesRequireANonNegativeExpectedVersion(String resource) throws Exception {
        String path = "/api/admin/" + resource + "/1";
        mvc.perform(request(HttpMethod.DELETE, path)).andExpect(status().isBadRequest());
        mvc.perform(request(HttpMethod.DELETE, path).param("expectedVersion", "-1"))
                .andExpect(status().isBadRequest());
        mvc.perform(request(HttpMethod.DELETE, path).param("expectedVersion", "0"))
                .andExpect(status().isNotImplemented());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/teachings/0", "/api/admin/supervisions/0/1",
            "/api/admin/supervisions/1/0", "/api/admin/equipment/0", "/api/admin/timetable/0"})
    void unversionedDeletesStillValidateEveryPathId(String path) throws Exception {
        mvc.perform(request(HttpMethod.DELETE, path)).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @MethodSource("invalidFilters")
    void catalogFiltersRejectInvalidIdsAndEnums(String path, String parameter, String value) throws Exception {
        mvc.perform(get(path).param(parameter, value)).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?dryRun=true&allowPartial=false", "?dryRun=false&allowPartial=false",
            "?dryRun=false&allowPartial=true"})
    void architectureImportAcceptsExternalReferencesAndOptionalPasswordHashes(String query) throws Exception {
        mvc.perform(post("/api/admin/import" + query).contentType(MediaType.APPLICATION_JSON).content(IMPORT))
                .andExpect(status().isNotImplemented())
                .andExpect(content().string(not(containsString("contract-secret"))))
                .andExpect(content().string(not(containsString("ivanov@example.org"))));
    }

    @Test
    void importsOnlinePlatformWithoutCapacityAndRejectsInconsistentCapacity() throws Exception {
        mvc.perform(post("/api/admin/import").contentType(MediaType.APPLICATION_JSON)
                        .content(IMPORT.replace("\"capacity\":30", "\"online\":true,\"capacity\":null")))
                .andExpect(status().isNotImplemented());
        mvc.perform(post("/api/admin/import").contentType(MediaType.APPLICATION_JSON)
                        .content(IMPORT.replace("\"capacity\":30", "\"online\":true,\"capacity\":30")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/import").contentType(MediaType.APPLICATION_JSON)
                        .content(IMPORT.replace("\"capacity\":30", "\"capacity\":null")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nestedImportValidationDoesNotEchoCredentials() throws Exception {
        mvc.perform(post("/api/admin/import").contentType(MediaType.APPLICATION_JSON)
                        .content(IMPORT.replace("{bcrypt}$2a$10$contract-secret", "plain-contract-secret")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'accounts[1].passwordHash')]").isNotEmpty())
                .andExpect(content().string(not(containsString("plain-contract-secret"))));
        mvc.perform(post("/api/admin/import").contentType(MediaType.APPLICATION_JSON)
                        .content(IMPORT.replace("\"students\":[\"s-1001\"]", "\"students\":[null]")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/import").contentType(MediaType.APPLICATION_JSON)
                        .content(IMPORT.replace("\"lastName\":\"Иванов\"", "\"lastName\":\"" + "я".repeat(101) + "\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void importRejectsMalformedJsonCsvAndMalformedFlags() throws Exception {
        mvc.perform(post("/api/admin/import").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/import").contentType("text/csv").content("externalId,email"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(post("/api/admin/import").param("dryRun", "maybe")
                        .contentType(MediaType.APPLICATION_JSON).content(IMPORT))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"S", "M", "L", "TIGHT"})
    void demoDatasetsUseAnExplicitSeed(String dataset) throws Exception {
        mvc.perform(post("/api/admin/demo-data").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataset\":\"" + dataset + "\",\"seed\":0}"))
                .andExpect(status().isNotImplemented());
    }

    @Test
    void openApiUsesTypedSuccessResponsesAndDocumentsRequiredNullableFields() throws Exception {
        DocumentContext document = specification();
        assertEquals("listUniversityPeople", document.read("$.paths['/api/university/people'].get.operationId"));
        assertEquals("#/components/schemas/PageResponsePersonSummary", document.read(
                "$.paths['/api/university/people'].get.responses['200'].content['application/json'].schema['$ref']"));
        assertRequired(document, "UniversityGroupResponse", "id", "externalId", "name", "active", "version", "studentIds");
        assertRequired(document, "UniversityRoomResponse", "building", "capacity", "online", "equipmentIds");
        assertRequired(document, "UniversityTimetableEntryResponse", "accountId", "roomId", "description", "startsAt", "endsAt");
        assertRequired(document, "UniversityImportResponse", "dryRun", "allowPartial", "applied", "summary", "sections", "diagnostics");
        Map<String, Map<String, Object>> paths = document.read("$.paths");
        for (String resource : CATALOGS) {
            String adminPath = "/api/admin/" + resource;
            assertTrue(paths.entrySet().stream()
                    .filter(entry -> entry.getKey().equals(adminPath) || entry.getKey().startsWith(adminPath + "/"))
                    .noneMatch(entry -> entry.getValue().containsKey("get")), adminPath);
        }
        for (String resource : List.of("groups", "rooms", "disciplines", "equipment", "timetable")) {
            assertNotNull(document.read("$.paths['/api/admin/" + resource + "'].post.responses['201']"));
            assertNotNull(document.read("$.paths['/api/admin/" + resource + "/{id}'].delete.responses['204']"));
        }
        Map<String, Object> capacity = document.read("$.components.schemas.UniversityRoomResponse.properties.capacity");
        assertTrue(isNullable(capacity), "Online rooms expose a required, nullable capacity");
        Map<String, Object> building = document.read("$.components.schemas.UniversityRoomResponse.properties.building");
        assertTrue(isNullable(building), "A building can be absent");
        Map<String, Object> equipment = document.read("$.components.schemas.UniversityEquipmentResponse.properties");
        assertFalse(equipment.containsKey("version"), "Equipment has no B1 optimistic version");
    }

    @Test
    void openApiImportIsJsonOnlyUsesExactArchitectureKeysAndKeepsHashesWriteOnly() throws Exception {
        DocumentContext document = specification();
        Map<String, Object> content = document.read("$.paths['/api/admin/import'].post.requestBody.content");
        assertEquals(List.of("application/json"), List.copyOf(content.keySet()));
        Map<String, Object> properties = document.read("$.components.schemas.UniversityImportRequest.properties");
        assertEquals(7, properties.size());
        assertTrue(properties.keySet().containsAll(List.of("accounts", "groups", "rooms", "disciplines",
                "teaching", "supervision", "timetable")));
        assertEquals(Boolean.TRUE, document.read("$.components.schemas.UniversityImportAccount.properties.passwordHash.writeOnly"));
        List<String> required = document.read("$.components.schemas.UniversityImportAccount.required");
        assertFalse(required.contains("passwordHash"));
        List<Map<String, Object>> parameters = document.read("$.paths['/api/admin/import'].post.parameters");
        assertEquals(Boolean.TRUE, parameterSchema(parameters, "dryRun").get("default"));
        assertEquals(Boolean.FALSE, parameterSchema(parameters, "allowPartial").get("default"));
        assertRequired(document, "UniversityImportCounts", "total", "created", "updated", "unchanged", "rejected");
        assertRequired(document, "UniversityImportDiagnostic", "path", "issue");
        Map<String, Map<String, Object>> schemas = document.read("$.components.schemas");
        for (var schema : schemas.entrySet()) {
            if (schema.getKey().startsWith("University") && !schema.getKey().equals("UniversityImportAccount")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> fields = (Map<String, Object>) schema.getValue().get("properties");
                if (fields != null) assertFalse(fields.containsKey("passwordHash"), schema.getKey());
            }
        }
    }

    @Test
    void openApiCatalogsExposePaginationDefaultsAndBounds() throws Exception {
        DocumentContext document = specification();
        for (String path : catalogPaths().toList()) {
            List<Map<String, Object>> parameters = document.read("$.paths['" + path + "'].get.parameters");
            Map<String, Object> page = parameterSchema(parameters, "page");
            Map<String, Object> size = parameterSchema(parameters, "size");
            assertEquals(0, ((Number) page.get("default")).intValue(), path);
            assertEquals(0, ((Number) page.get("minimum")).intValue(), path);
            assertEquals(20, ((Number) size.get("default")).intValue(), path);
            assertEquals(1, ((Number) size.get("minimum")).intValue(), path);
            assertEquals(100, ((Number) size.get("maximum")).intValue(), path);
            assertNotNull(parameterSchema(parameters, "search"), path);
        }
    }

    private DocumentContext specification() throws Exception {
        return JsonPath.parse(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private static void assertRequired(DocumentContext document, String schema, String... fields) {
        List<String> required = document.read("$.components.schemas." + schema + ".required");
        assertTrue(required.containsAll(List.of(fields)), schema + " must require " + List.of(fields));
    }

    private static boolean isNullable(Map<String, Object> schema) {
        Object type = schema.get("type");
        return Boolean.TRUE.equals(schema.get("nullable")) || type instanceof List<?> types && types.contains("null");
    }

    private static Map<String, Object> parameterSchema(List<Map<String, Object>> parameters, String name) {
        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) parameters.stream()
                .filter(parameter -> name.equals(parameter.get("name"))).findFirst().orElseThrow().get("schema");
        return schema;
    }

    private static Stream<String> catalogPaths() {
        return CATALOGS.stream().map(name -> "/api/university/" + name);
    }

    private static Stream<Arguments> invalidCatalogQueries() {
        return catalogPaths().flatMap(path -> Stream.of(Arguments.of(path, "page", "-1"),
                Arguments.of(path, "size", "0"), Arguments.of(path, "size", "101"),
                Arguments.of(path, "search", "x".repeat(256))));
    }

    private static Stream<Arguments> validMutations() {
        return Stream.of(
                Arguments.of("POST", "/api/admin/groups", "{\"name\":\"P3410\",\"studentIds\":[1]}"),
                Arguments.of("PUT", "/api/admin/groups/1", "{\"name\":\"P3410\",\"studentIds\":[],\"active\":true,\"expectedVersion\":0}"),
                Arguments.of("POST", "/api/admin/rooms", "{\"name\":\"1404\",\"online\":false,\"capacity\":30,\"equipmentIds\":[1]}"),
                Arguments.of("PUT", "/api/admin/rooms/1", "{\"name\":\"Online\",\"online\":true,\"equipmentIds\":[],\"active\":true,\"expectedVersion\":0}"),
                Arguments.of("POST", "/api/admin/disciplines", "{\"name\":\"Базы данных\"}"),
                Arguments.of("PUT", "/api/admin/disciplines/1", "{\"name\":\"Базы данных\",\"active\":false,\"expectedVersion\":0}"),
                Arguments.of("POST", "/api/admin/equipment", "{\"name\":\"" + "e".repeat(100) + "\"}"),
                Arguments.of("PUT", "/api/admin/equipment/1", "{\"name\":\"Projector\"}"),
                Arguments.of("DELETE", "/api/admin/equipment/1", ""),
                Arguments.of("POST", "/api/admin/teachings", "{\"teacherId\":1,\"disciplineId\":2,\"groupId\":3}"),
                Arguments.of("DELETE", "/api/admin/teachings/1", ""),
                Arguments.of("POST", "/api/admin/supervisions", "{\"studentId\":1,\"supervisorId\":2}"),
                Arguments.of("DELETE", "/api/admin/supervisions/1/2", ""),
                Arguments.of("POST", "/api/admin/timetable", TIMETABLE),
                Arguments.of("PUT", "/api/admin/timetable/1", TIMETABLE),
                Arguments.of("DELETE", "/api/admin/timetable/1", ""));
    }

    private static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of("POST", "/api/admin/groups", "{}"),
                Arguments.of("POST", "/api/admin/groups", "{\"name\":\" \",\"studentIds\":[]}"),
                Arguments.of("POST", "/api/admin/groups", "{\"name\":\"P3410\",\"studentIds\":[0]}"),
                Arguments.of("POST", "/api/admin/groups", "{\"name\":\"" + "g".repeat(256) + "\",\"studentIds\":[]}"),
                Arguments.of("PUT", "/api/admin/groups/1", "{\"name\":\"P3410\",\"studentIds\":[],\"active\":true}"),
                Arguments.of("PUT", "/api/admin/groups/1", "{\"name\":\"P3410\",\"studentIds\":[],\"active\":true,\"expectedVersion\":-1}"),
                Arguments.of("POST", "/api/admin/rooms", "{\"name\":\"1404\",\"online\":false,\"capacity\":0,\"equipmentIds\":[]}"),
                Arguments.of("POST", "/api/admin/rooms", "{\"name\":\"1404\",\"online\":false,\"capacity\":30,\"equipmentIds\":[null]}"),
                Arguments.of("PUT", "/api/admin/rooms/1", "{\"name\":\"1404\",\"online\":false,\"capacity\":30,\"equipmentIds\":[],\"active\":true}"),
                Arguments.of("PUT", "/api/admin/disciplines/1", "{\"name\":\"DB\",\"active\":true,\"expectedVersion\":null}"),
                Arguments.of("POST", "/api/admin/equipment", "{\"name\":\"" + "e".repeat(101) + "\"}"),
                Arguments.of("POST", "/api/admin/teachings", "{\"teacherId\":0,\"disciplineId\":1,\"groupId\":1}"),
                Arguments.of("POST", "/api/admin/supervisions", "{\"studentId\":1}"),
                Arguments.of("POST", "/api/admin/timetable", "{\"accountId\":1,\"endsAt\":\"2026-06-15T08:20:00Z\"}"),
                Arguments.of("POST", "/api/admin/demo-data", "{\"dataset\":\"S\"}"));
    }

    private static Stream<Arguments> invalidFilters() {
        return Stream.of(
                Arguments.of("/api/university/people", "role", "UNKNOWN"),
                Arguments.of("/api/university/people", "groupId", "0"),
                Arguments.of("/api/university/groups", "studentId", "-1"),
                Arguments.of("/api/university/rooms", "equipmentId", "0"),
                Arguments.of("/api/university/rooms", "minCapacity", "0"),
                Arguments.of("/api/university/teachings", "teacherId", "0"),
                Arguments.of("/api/university/supervisions", "supervisorId", "0"),
                Arguments.of("/api/university/timetable", "accountId", "0"));
    }
}
