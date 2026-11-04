package ru.vk.itmo.scheduler.slotwise.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SolvingScheduleContractTests extends ApiContractTestBase {

    private static final String SOLVE = """
            {"expectedInputVersion":0,"algorithm":"CP_SAT","timeLimitSeconds":1,"seed":-1}
            """;
    private static final String ASSIGNMENT = """
            {"expectedVariantVersion":0,"expectedAssignmentVersion":0,"status":"ASSIGNED",
             "startsAt":"2027-01-15T06:00:00Z","endsAt":"2027-01-15T06:30:00Z","roomId":1,
             "commissionMembers":[{"accountId":1,"chair":true}],"locked":false}
            """;
    private static final String PUBLISH = """
            {"expectedVersion":0,"confirmUnassigned":false,"confirmStaleData":false}
            """;

    static Stream<MockHttpServletRequestBuilder> validRequests() {
        return Stream.of(
                get("/api/solvers"),
                post("/api/sessions/1/precheck").content("{\"expectedInputVersion\":0}"),
                post("/api/sessions/1/solve").content(SOLVE),
                post("/api/sessions/1/solve").content(SOLVE.replace("\"seed\":-1", "\"seed\":0,\"baseVariantId\":1")),
                get("/api/jobs/1"),
                get("/api/sessions/1/variants").param("page", "0").param("size", "100"),
                get("/api/variants/1"),
                patch("/api/variants/1/assignments/2").content(ASSIGNMENT),
                patch("/api/variants/1/assignments/2").param("dryRun", "true").content(ASSIGNMENT),
                patch("/api/variants/1/assignments/2").content("""
                        {"expectedVariantVersion":0,"expectedAssignmentVersion":0,"status":"UNASSIGNED",
                         "startsAt":null,"endsAt":null,"roomId":null,"commissionMembers":[],"locked":false}
                        """),
                post("/api/variants/1/publish").content(PUBLISH),
                get("/api/me/schedule").param("from", "2027-01-01T00:00:00Z")
                        .param("to", "2027-02-01T00:00:00Z"));
    }

    @ParameterizedTest
    @MethodSource("validRequests")
    void validContractsReturnAnHonest501Problem(MockHttpServletRequestBuilder request) throws Exception {
        mvc.perform(request.contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotImplemented())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(501))
                .andExpect(jsonPath("$.code").value("NOT_IMPLEMENTED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"expectedInputVersion\":-1}", "{\"expectedInputVersion\":null}"})
    void precheckRequiresTheExpectedInputVersion(String body) throws Exception {
        mvc.perform(post("/api/sessions/1/precheck").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors").isNotEmpty());
    }

    static Stream<String> invalidSolveRequests() {
        return Stream.of("{}", SOLVE.replace("\"expectedInputVersion\":0", "\"expectedInputVersion\":-1"),
                SOLVE.replace("\"algorithm\":\"CP_SAT\"", "\"algorithm\":null"),
                SOLVE.replace("\"algorithm\":\"CP_SAT\"", "\"algorithm\":\"UNKNOWN\""),
                SOLVE.replace("\"timeLimitSeconds\":1", "\"timeLimitSeconds\":0"),
                SOLVE.replace("\"seed\":-1", "\"seed\":null"),
                SOLVE.replace("\"seed\":-1", "\"seed\":0,\"baseVariantId\":0"));
    }

    @ParameterizedTest
    @MethodSource("invalidSolveRequests")
    void solveRejectsMissingOrOutOfRangeParameters(String body) throws Exception {
        mvc.perform(post("/api/sessions/1/solve").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    static Stream<String> invalidAssignmentRequests() {
        return Stream.of("{}", ASSIGNMENT.replace("\"expectedVariantVersion\":0", "\"expectedVariantVersion\":-1"),
                ASSIGNMENT.replace("\"expectedAssignmentVersion\":0", "\"expectedAssignmentVersion\":null"),
                ASSIGNMENT.replace("\"status\":\"ASSIGNED\"", "\"status\":null"),
                ASSIGNMENT.replace("\"roomId\":1", "\"roomId\":0"),
                ASSIGNMENT.replace("\"accountId\":1", "\"accountId\":0"),
                ASSIGNMENT.replace("\"chair\":true", "\"chair\":null"),
                ASSIGNMENT.replace("[{\"accountId\":1,\"chair\":true}]", "[null]"),
                ASSIGNMENT.replace("\"commissionMembers\":[{\"accountId\":1,\"chair\":true}]", "\"commissionMembers\":null"),
                ASSIGNMENT.replace("\"locked\":false", "\"locked\":null"));
    }

    @ParameterizedTest
    @MethodSource("invalidAssignmentRequests")
    void assignmentValidatesBothVersionsAndNestedCommission(String body) throws Exception {
        mvc.perform(patch("/api/variants/1/assignments/2").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors").isNotEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"expectedVersion\":-1,\"confirmUnassigned\":true,\"confirmStaleData\":true}",
            "{\"expectedVersion\":0,\"confirmStaleData\":true}",
            "{\"expectedVersion\":0,\"confirmUnassigned\":true}"})
    void publishRequiresAnExpectedVersionAndExplicitConfirmations(String body) throws Exception {
        mvc.perform(post("/api/variants/1/publish").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    static Stream<MockHttpServletRequestBuilder> invalidPathIds() {
        return Stream.of(get("/api/jobs/0"), get("/api/variants/-1"), get("/api/sessions/0/variants"),
                post("/api/sessions/0/precheck").content("{\"expectedInputVersion\":0}"),
                post("/api/sessions/-1/solve").content(SOLVE),
                patch("/api/variants/0/assignments/2").content(ASSIGNMENT),
                patch("/api/variants/1/assignments/0").content(ASSIGNMENT),
                post("/api/variants/0/publish").content(PUBLISH));
    }

    @ParameterizedTest
    @MethodSource("invalidPathIds")
    void pathIdsMustBePositive(MockHttpServletRequestBuilder request) throws Exception {
        mvc.perform(request.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
    }

    @Test
    void validatesPaginationAndDryRun() throws Exception {
        mvc.perform(get("/api/sessions/1/variants").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/sessions/1/variants").param("size", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/sessions/1/variants").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/variants/1/assignments/2").param("dryRun", "maybe")
                .contentType(MediaType.APPLICATION_JSON).content(ASSIGNMENT)).andExpect(status().isBadRequest());
    }

    @Test
    void personalScheduleRequiresAnInstantRange() throws Exception {
        mvc.perform(get("/api/me/schedule")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/me/schedule").param("from", "2027-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/me/schedule").param("from", "2027-01-01")
                .param("to", "2027-02-01T00:00:00Z")).andExpect(status().isBadRequest());
    }

    @Test
    void documentsTypedResultsAndWholeVariantMutationSemantics() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/solvers'].get.operationId").value("listSolvers"))
                .andExpect(jsonPath("$.paths['/api/sessions/{id}/precheck'].post.operationId").value("precheckSession"))
                .andExpect(jsonPath("$.paths['/api/sessions/{id}/solve'].post.responses['202'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/SolverJobResponse"))
                .andExpect(jsonPath("$.paths['/api/jobs/{id}'].get.operationId").value("getSolverJob"))
                .andExpect(jsonPath("$.paths['/api/sessions/{id}/variants'].get.responses['200'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/PageResponseVariantSummaryResponse"))
                .andExpect(jsonPath("$.paths['/api/variants/{id}'].get.responses['200'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/VariantResponse"))
                .andExpect(jsonPath("$.paths['/api/variants/{id}/assignments/{eventId}'].patch.responses['200'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/AssignmentUpdateResponse"))
                .andExpect(jsonPath("$.paths['/api/variants/{id}/assignments/{eventId}'].patch.responses['422'].content['application/problem+json'].schema['$ref']")
                        .value("#/components/schemas/ApiProblem"))
                .andExpect(jsonPath("$.paths['/api/variants/{id}/publish'].post.responses['200'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/VariantResponse"));
    }

    @Test
    void jobAndMetricsExposeRequiredNullableFieldsAndStableEnums() throws Exception {
        String json = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var document = JsonPath.parse(json);
        List<String> jobRequired = document.read("$.components.schemas.SolverJobResponse.required");
        for (String field : List.of("progress", "queuePosition", "error", "resultId", "startedAt", "finishedAt")) {
            assertTrue(jobRequired.contains(field), "Polling field must always be present: " + field);
            Map<String, Object> property = document.read("$.components.schemas.SolverJobResponse.properties." + field);
            assertTrue(allowsNull(property), "Polling field must allow null: " + field);
        }
        List<String> metricsRequired = document.read("$.components.schemas.ScheduleMetrics.required");
        assertTrue(metricsRequired.containsAll(List.of("penalty", "hardViolations", "unassignedEvents", "gapsHours",
                "commissionLoadRangeHours", "commissionLoadStandardDeviationHours", "undesirableEvents",
                "teacherRoomChanges", "runtimeMillis", "bestBound", "gap", "solveStatus", "scoreComponents")));
        Map<String, Object> metricProperties = document.read("$.components.schemas.ScheduleMetrics.properties");
        for (String obsolete : List.of("objective", "unassignedPenalty", "loadRangeHours", "loadStandardDeviationHours")) {
            assertFalse(metricProperties.containsKey(obsolete), "Obsolete metric must not be exposed: " + obsolete);
        }
        for (String field : List.of("penalty", "bestBound", "gap")) {
            Map<String, Object> property = document.read("$.components.schemas.ScheduleMetrics.properties." + field);
            assertTrue(allowsNull(property), "Unavailable metric must allow null: " + field);
        }
        List<String> constraints = document.read("$.components.schemas.ScheduleConstraint.enum");
        assertEquals(Set.of("S1", "S2", "S3", "S4", "S5"), Set.copyOf(constraints));
        List<String> jobStatuses = document.read("$.components.schemas.SolverJobStatus.enum");
        assertEquals(Set.of("QUEUED", "RUNNING", "COMPLETED", "FAILED", "CANCELLED"), Set.copyOf(jobStatuses));
        List<String> solveStatuses = document.read("$.components.schemas.SolveStatus.enum");
        assertEquals(Set.of("OPTIMAL", "FEASIBLE", "NO_SOLUTION"), Set.copyOf(solveStatuses));
    }

    @Test
    void personalScheduleHasNoUserOverrideOrPrivateParticipantData() throws Exception {
        String json = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var document = JsonPath.parse(json);
        List<Map<String, Object>> parameters = document.read("$.paths['/api/me/schedule'].get.parameters");
        assertEquals(Set.of("from", "to"), parameters.stream().map(parameter -> parameter.get("name")).collect(
                java.util.stream.Collectors.toSet()));
        for (var parameter : parameters) {
            assertEquals(true, parameter.get("required"));
        }
        Map<String, Map<String, Object>> schemas = document.read("$.components.schemas");
        assertPublicScheduleSchema(schemas, "PersonalScheduleResponse", new java.util.HashSet<>());
        Map<String, Object> publication = document.read("$.components.schemas.PublishVariantRequest.properties");
        assertEquals(Set.of("expectedVersion", "confirmUnassigned", "confirmStaleData"), publication.keySet());
    }

    private static boolean allowsNull(Map<String, Object> schema) {
        return Boolean.TRUE.equals(schema.get("nullable"))
                || schema.get("type") instanceof List<?> types && types.contains("null")
                || Stream.of("oneOf", "anyOf").anyMatch(key -> schema.get(key) instanceof List<?> alternatives
                && alternatives.stream().anyMatch(value -> value instanceof Map<?, ?> item
                && "null".equals(item.get("type"))));
    }

    private static void assertPublicScheduleSchema(Map<String, Map<String, Object>> schemas, String name, Set<String> seen) {
        if (!seen.add(name)) return;
        Map<String, Object> schema = schemas.get(name);
        assertTrue(schema != null, "Missing referenced schema " + name);
        assertPublicScheduleNode(schemas, schema, seen);
    }

    private static void assertPublicScheduleNode(Map<String, Map<String, Object>> schemas, Object node, Set<String> seen) {
        if (node instanceof Map<?, ?> map) {
            if (map.get("properties") instanceof Map<?, ?> properties) {
                for (Object field : properties.keySet()) {
                    String lower = field.toString().toLowerCase(java.util.Locale.ROOT);
                    assertFalse(lower.contains("email") || lower.contains("phone") || lower.contains("busy")
                            || lower.contains("password") || lower.contains("contact"), "Private field: " + field);
                }
            }
            if (map.get("$ref") instanceof String reference) {
                assertPublicScheduleSchema(schemas, reference.substring(reference.lastIndexOf('/') + 1), seen);
            }
            map.values().forEach(value -> assertPublicScheduleNode(schemas, value, seen));
        } else if (node instanceof List<?> list) {
            list.forEach(value -> assertPublicScheduleNode(schemas, value, seen));
        }
    }
}
