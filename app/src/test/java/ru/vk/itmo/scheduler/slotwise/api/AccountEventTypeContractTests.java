package ru.vk.itmo.scheduler.slotwise.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountEventTypeContractTests extends ApiContractTestBase {
    private static final String PROFILE = """
            {"externalId":null,"email":"student@example.org","lastName":"Иванов","firstName":"Иван",
             "patronymic":null,"timeZone":"Europe/Moscow"}
            """;
    private static final String CREATE_ACCOUNT = PROFILE.replace("\"externalId\":null",
            "\"role\":\"STUDENT\",\"externalId\":null");
    private static final String UPDATE_PROFILE = PROFILE.replace("\"externalId\":null",
            "\"expectedVersion\":0,\"externalId\":null");
    private static final String CREATE_EVENT_TYPE = """
            {"name":"Защита","defaultDurationMinutes":15,"preparationDays":0,"granularity":"PER_STUDENT",
             "defaultFormat":"ONSITE","commissionSize":0,"requiresChair":false,"active":true,
             "participantRoles":["STUDENT"],"equipmentIds":[],"dependencies":[]}
            """;
    private static final String UPDATE_EVENT_TYPE = CREATE_EVENT_TYPE.replace("\"name\":",
            "\"expectedVersion\":0,\"name\":");

    @ParameterizedTest(name = "{0} {1} returns the contract stub")
    @MethodSource("validOperations")
    void validOperationsReturnNotImplemented(String method, String path, String body) throws Exception {
        var operation = request(HttpMethod.valueOf(method), path);
        if (body != null) {
            operation.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        mvc.perform(operation)
                .andExpect(status().isNotImplemented())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(501))
                .andExpect(jsonPath("$.code").value("NOT_IMPLEMENTED"));
    }

    static Stream<Arguments> validOperations() {
        return Stream.of(
                Arguments.of("GET", "/api/admin/accounts", null),
                Arguments.of("POST", "/api/admin/accounts", CREATE_ACCOUNT),
                Arguments.of("GET", "/api/admin/accounts/1", null),
                Arguments.of("PATCH", "/api/admin/accounts/1", UPDATE_PROFILE),
                Arguments.of("PUT", "/api/admin/accounts/1/role", "{\"expectedVersion\":0,\"role\":\"ADMIN\"}"),
                Arguments.of("PUT", "/api/admin/accounts/1/enabled", "{\"expectedVersion\":0,\"enabled\":false}"),
                Arguments.of("PUT", "/api/admin/accounts/1/password",
                        "{\"expectedVersion\":0,\"password\":\"" + "x".repeat(128) + "\"}"),
                Arguments.of("GET", "/api/event-types", null),
                Arguments.of("POST", "/api/event-types", CREATE_EVENT_TYPE),
                Arguments.of("GET", "/api/event-types/1", null),
                Arguments.of("PUT", "/api/event-types/1", UPDATE_EVENT_TYPE),
                Arguments.of("DELETE", "/api/event-types/1?expectedVersion=0", null));
    }

    @Test
    void accountFiltersAndPaginationAreAccepted() throws Exception {
        mvc.perform(get("/api/admin/accounts").param("search", "Иванов").param("role", "TEACHER")
                        .param("enabled", "false").param("page", "0").param("size", "100"))
                .andExpect(status().isNotImplemented());
        mvc.perform(get("/api/event-types").param("search", "Защита").param("active", "true")
                        .param("page", "0").param("size", "100"))
                .andExpect(status().isNotImplemented());
    }

    @ParameterizedTest
    @CsvSource({
            "/api/admin/accounts,page,-1", "/api/admin/accounts,size,0", "/api/admin/accounts,size,101",
            "/api/admin/accounts,role,UNKNOWN", "/api/admin/accounts,enabled,UNKNOWN",
            "/api/event-types,page,-1", "/api/event-types,size,0", "/api/event-types,size,101",
            "/api/event-types,active,UNKNOWN"
    })
    void invalidFiltersAndPaginationAreClientErrors(String path, String parameter, String value) throws Exception {
        mvc.perform(get(path).param(parameter, value))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest(name = "{0} rejects non-positive id {1}")
    @CsvSource({"/api/admin/accounts,0", "/api/admin/accounts,-1", "/api/event-types,0", "/api/event-types,-1"})
    void nonPositiveIdsAreClientErrors(String path, String id) throws Exception {
        mvc.perform(get(path + "/" + id))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest(name = "account profile rejects invalid {0}")
    @MethodSource("invalidProfiles")
    void accountProfilesEnforceB1Boundaries(String field, String replacement) throws Exception {
        String original = switch (field) {
            case "email" -> "\"email\":\"student@example.org\"";
            case "lastName" -> "\"lastName\":\"Иванов\"";
            case "firstName" -> "\"firstName\":\"Иван\"";
            case "timeZone" -> "\"timeZone\":\"Europe/Moscow\"";
            default -> "\"" + field + "\":null";
        };
        for (var operation : List.of(post("/api/admin/accounts"), request(HttpMethod.PATCH, "/api/admin/accounts/1"))) {
            String body = operation.buildRequest(null).getMethod().equals("POST") ? CREATE_ACCOUNT : UPDATE_PROFILE;
            mvc.perform(operation.contentType(MediaType.APPLICATION_JSON)
                            .content(body.replace(original, "\"" + field + "\":" + replacement)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fieldErrors[*].field", hasItem(field)));
        }
    }

    static Stream<Arguments> invalidProfiles() {
        return Stream.of(
                Arguments.of("email", "null"), Arguments.of("email", "\"\""),
                Arguments.of("email", "\"invalid-email\""),
                Arguments.of("email", "\"" + "x".repeat(309) + "@example.org\""),
                Arguments.of("lastName", "\" \""), Arguments.of("lastName", "\"" + "x".repeat(101) + "\""),
                Arguments.of("firstName", "null"), Arguments.of("firstName", "\"" + "x".repeat(101) + "\""),
                Arguments.of("patronymic", "\"" + "x".repeat(101) + "\""),
                Arguments.of("externalId", "\"" + "x".repeat(256) + "\""),
                Arguments.of("timeZone", "\"\""), Arguments.of("timeZone", "\"" + "x".repeat(65) + "\""));
    }

    @Test
    void accountNameAndExternalIdLimitsAreInclusive() throws Exception {
        String body = CREATE_ACCOUNT.replace("\"lastName\":\"Иванов\"", "\"lastName\":\"" + "x".repeat(100) + "\"")
                .replace("\"firstName\":\"Иван\"", "\"firstName\":\"" + "x".repeat(100) + "\"")
                .replace("\"patronymic\":null", "\"patronymic\":\"" + "x".repeat(100) + "\"")
                .replace("\"externalId\":null", "\"externalId\":\"" + "x".repeat(255) + "\"");
        mvc.perform(post("/api/admin/accounts").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotImplemented());
    }

    @ParameterizedTest
    @MethodSource("versionedBodies")
    void accountAndTemplateMutationsRequireANonNegativeVersion(String method, String path, String body) throws Exception {
        for (String invalidVersion : List.of("null", "-1")) {
            mvc.perform(request(HttpMethod.valueOf(method), path).contentType(MediaType.APPLICATION_JSON)
                            .content(body.replace("\"expectedVersion\":0", "\"expectedVersion\":" + invalidVersion)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("expectedVersion")));
        }
        mvc.perform(request(HttpMethod.valueOf(method), path).contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("\"expectedVersion\":0,", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("expectedVersion")));
    }

    static Stream<Arguments> versionedBodies() {
        return Stream.of(
                Arguments.of("PATCH", "/api/admin/accounts/1", UPDATE_PROFILE),
                Arguments.of("PUT", "/api/admin/accounts/1/role", "{\"expectedVersion\":0,\"role\":\"ORGANIZER\"}"),
                Arguments.of("PUT", "/api/admin/accounts/1/enabled", "{\"expectedVersion\":0,\"enabled\":true}"),
                Arguments.of("PUT", "/api/admin/accounts/1/password", "{\"expectedVersion\":0,\"password\":\"secret\"}"),
                Arguments.of("PUT", "/api/event-types/1", UPDATE_EVENT_TYPE));
    }

    @ParameterizedTest
    @MethodSource("invalidAccountActions")
    void dedicatedAccountActionsValidateTheirPayload(String action, String body) throws Exception {
        mvc.perform(request(HttpMethod.PUT, "/api/admin/accounts/1/" + action)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    static Stream<Arguments> invalidAccountActions() {
        return Stream.of(
                Arguments.of("role", "{\"expectedVersion\":0,\"role\":null}"),
                Arguments.of("role", "{\"expectedVersion\":0,\"role\":\"UNKNOWN\"}"),
                Arguments.of("enabled", "{\"expectedVersion\":0,\"enabled\":null}"),
                Arguments.of("password", "{\"expectedVersion\":0,\"password\":null}"),
                Arguments.of("password", "{\"expectedVersion\":0,\"password\":\" \"}"),
                Arguments.of("password", "{\"expectedVersion\":0,\"password\":\"" + "x".repeat(129) + "\"}"));
    }

    @ParameterizedTest(name = "template rejects {0}")
    @MethodSource("invalidEventTypes")
    void templateCreationAndUpdateValidateNestedCollectionsAndSlotGrid(String original, String replacement) throws Exception {
        mvc.perform(post("/api/event-types").contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_EVENT_TYPE.replace(original, replacement)))
                .andExpect(status().isBadRequest());
        mvc.perform(request(HttpMethod.PUT, "/api/event-types/1").contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_EVENT_TYPE.replace(original, replacement)))
                .andExpect(status().isBadRequest());
    }

    static Stream<Arguments> invalidEventTypes() {
        return Stream.of(
                Arguments.of("\"name\":\"Защита\"", "\"name\":\" \""),
                Arguments.of("\"name\":\"Защита\"", "\"name\":\"" + "x".repeat(256) + "\""),
                Arguments.of("\"defaultDurationMinutes\":15", "\"defaultDurationMinutes\":0"),
                Arguments.of("\"defaultDurationMinutes\":15", "\"defaultDurationMinutes\":14"),
                Arguments.of("\"defaultDurationMinutes\":15", "\"defaultDurationMinutes\":16"),
                Arguments.of("\"defaultDurationMinutes\":15", "\"defaultDurationMinutes\":null"),
                Arguments.of("\"commissionSize\":0", "\"commissionSize\":-1"),
                Arguments.of("\"commissionSize\":0", "\"commissionSize\":null"),
                Arguments.of("\"requiresChair\":false", "\"requiresChair\":null"),
                Arguments.of("\"active\":true", "\"active\":null"),
                Arguments.of("[\"STUDENT\"]", "[]"), Arguments.of("[\"STUDENT\"]", "[null]"),
                Arguments.of("[\"STUDENT\"]", "[\"UNKNOWN\"]"),
                Arguments.of("\"granularity\":\"PER_STUDENT\"", "\"granularity\":\"UNKNOWN\""),
                Arguments.of("\"defaultFormat\":\"ONSITE\"", "\"defaultFormat\":\"UNKNOWN\""),
                Arguments.of("\"equipmentIds\":[]", "\"equipmentIds\":null"),
                Arguments.of("\"equipmentIds\":[]", "\"equipmentIds\":[0]"),
                Arguments.of("\"equipmentIds\":[]", "\"equipmentIds\":[null]"),
                Arguments.of("\"dependencies\":[]", "\"dependencies\":null"),
                Arguments.of("\"dependencies\":[]", "\"dependencies\":[null]"),
                Arguments.of("\"dependencies\":[]", "\"dependencies\":[{\"predecessorTypeId\":0,\"minGapDays\":0}]"),
                Arguments.of("\"dependencies\":[]", "\"dependencies\":[{\"predecessorTypeId\":1,\"minGapDays\":-1}]"),
                Arguments.of("\"dependencies\":[]", "\"dependencies\":[{\"predecessorTypeId\":1}]"));
    }

    @Test
    void nonEmptyTemplateRelationsAndZeroDayGapAreAccepted() throws Exception {
        String body = CREATE_EVENT_TYPE.replace("\"name\":\"Защита\"", "\"name\":\"" + "x".repeat(255) + "\"")
                .replace("\"defaultDurationMinutes\":15", "\"defaultDurationMinutes\":30")
                .replace("\"equipmentIds\":[]", "\"equipmentIds\":[1]")
                .replace("\"dependencies\":[]", "\"dependencies\":[{\"predecessorTypeId\":1,\"minGapDays\":0}]");
        mvc.perform(post("/api/event-types").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotImplemented());
    }

    @Test
    void templateDeletionRequiresAVersion() throws Exception {
        mvc.perform(delete("/api/event-types/1"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/event-types/1").param("expectedVersion", "-1"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/event-types/0").param("expectedVersion", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedBodiesAreClientErrors() throws Exception {
        for (String path : List.of("/api/admin/accounts", "/api/event-types")) {
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
    }

    @Test
    void openApiDescribesSuccessStatusesAndPasswordInputWithoutExposingSecrets() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/admin/accounts'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/admin/accounts/{id}/password'].put.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/event-types'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/event-types/{id}'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.components.schemas.AccountResponse.properties.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.AccountResponse.properties.password").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.CreateAccountRequest.properties.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.CreateAccountRequest.properties.password").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.SetAccountPasswordRequest.properties.password.writeOnly").value(true))
                .andExpect(jsonPath("$.components.schemas.SetAccountPasswordRequest.properties.password.maxLength").value(128))
                .andExpect(jsonPath("$.components.schemas.CreateEventTypeRequest.properties.defaultDurationMinutes.multipleOf").value(5))
                .andExpect(jsonPath("$.components.schemas.UpdateEventTypeRequest.properties.defaultDurationMinutes.multipleOf").value(5))
                .andExpect(jsonPath("$.components.schemas.AccountResponse.required", hasItems(
                        "id", "externalId", "email", "lastName", "firstName", "patronymic", "role", "timeZone",
                        "enabled", "hasPassword", "version", "createdAt", "updatedAt")))
                .andExpect(jsonPath("$.components.schemas.EventTypeResponse.required", hasItems(
                        "id", "name", "defaultDurationMinutes", "granularity", "defaultFormat", "commissionSize",
                        "requiresChair", "active", "participantRoles", "equipmentIds", "dependencies", "version")));
    }

    @Test
    void accountResponseNullableFieldsRemainRequiredForConsumers() throws Exception {
        String json = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var document = JsonPath.parse(json);
        for (String field : List.of("externalId", "patronymic")) {
            Map<String, Object> schema = document.read("$.components.schemas.AccountResponse.properties." + field);
            Object type = schema.get("type");
            assertTrue(Boolean.TRUE.equals(schema.get("nullable")) || type instanceof List<?> types && types.contains("null"),
                    "AccountResponse." + field + " must allow null");
        }
        Map<String, Object> page = document.read(
                "$.paths['/api/admin/accounts'].get.responses['200'].content['application/json'].schema");
        assertNotNull(page.get("$ref"));
        Map<String, Object> schemas = document.read("$.components.schemas");
        assertFalse(schemas.containsKey("AccountEntity"));
        assertFalse(schemas.containsKey("EventTypeEntity"));
        assertEquals("listAccounts", document.read("$.paths['/api/admin/accounts'].get.operationId"));
        assertEquals("listEventTypes", document.read("$.paths['/api/event-types'].get.operationId"));
    }
}
