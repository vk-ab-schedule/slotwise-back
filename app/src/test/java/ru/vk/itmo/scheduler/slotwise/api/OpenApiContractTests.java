package ru.vk.itmo.scheduler.slotwise.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpenApiContractTests extends ApiContractTestBase {

    private static final String CREATE_SESSION = """
            {
              "name":"Защиты ВКР", "startsAt":"2027-01-15T06:00:00Z", "endsAt":"2027-01-20T16:00:00Z",
              "timeZone":"Europe/Moscow", "workingDays":["MONDAY","TUESDAY","WEDNESDAY"],
              "workingDayStart":"09:00:00", "workingDayEnd":"19:00:00",
              "collectionStartsAt":"2027-01-10T06:00:00Z", "collectionEndsAt":"2027-01-11T16:00:00Z"
            }
            """;

    @Test
    void documentsEveryMvpArea() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.operationId").value("login"))
                .andExpect(jsonPath("$.paths['/api/auth/logout'].post.operationId").value("logout"))
                .andExpect(jsonPath("$.paths['/api/auth/me'].get.operationId").value("getCurrentUser"))
                .andExpect(jsonPath("$.paths['/api/admin/accounts'].post").exists())
                .andExpect(jsonPath("$.paths['/api/admin/import'].post").exists())
                .andExpect(jsonPath("$.paths['/api/admin/demo-data'].post").exists())
                .andExpect(jsonPath("$.paths['/api/university/groups'].get").exists())
                .andExpect(jsonPath("$.paths['/api/event-types'].get").exists())
                .andExpect(jsonPath("$.paths['/api/sessions'].post").exists())
                .andExpect(jsonPath("$.paths['/api/sessions/{id}/events'].post").exists())
                .andExpect(jsonPath("$.paths['/api/sessions/{id}/participants'].put").exists())
                .andExpect(jsonPath("$.paths['/api/sessions/{id}/commission-pool'].put").exists())
                .andExpect(jsonPath("$.paths['/api/sessions/{id}/rooms'].put").exists())
                .andExpect(jsonPath("$.paths['/api/sessions/{id}/collection'].get").exists())
                .andExpect(jsonPath("$.paths['/api/sessions/{id}/collection/close'].post").exists())
                .andExpect(jsonPath("$.paths['/api/sessions/{id}/busy-intervals/me'].put").exists())
                .andExpect(jsonPath("$.paths['/api/sessions/{id}/busy-intervals/me/submit'].post").exists())
                .andExpect(jsonPath("$.paths['/api/sessions/{id}/rules'].put").exists())
                .andExpect(jsonPath("$.paths['/api/sessions/{id}/precheck'].post").exists())
                .andExpect(jsonPath("$.paths['/api/sessions/{id}/solve'].post").exists())
                .andExpect(jsonPath("$.paths['/api/jobs/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/variants/{id}/assignments/{eventId}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/variants/{id}/publish'].post").exists())
                .andExpect(jsonPath("$.paths['/api/me/schedule'].get").exists());
    }

    @Test
    void validSessionRequestReturnsAnHonestNotImplementedProblem() throws Exception {
        mvc.perform(post("/api/sessions").contentType(MediaType.APPLICATION_JSON).content(CREATE_SESSION))
                .andExpect(status().isNotImplemented())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(501))
                .andExpect(jsonPath("$.code").value("NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.instance").value("/api/sessions"));
    }

    @Test
    void invalidSessionRequestExplainsFieldErrors() throws Exception {
        mvc.perform(post("/api/sessions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void exportsConsumerUsableSpecificationWithoutPostMvpOperations() throws Exception {
        String json = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var document = JsonPath.parse(json);
        Map<String, Map<String, Object>> paths = document.read("$.paths");
        assertTrue(paths.containsKey("/api/sessions"), "The consumer needs the planning API");
        Set<String> operationIds = new HashSet<>();
        for (var entry : paths.entrySet()) {
            String path = entry.getKey();
            assertFalse(path.contains("change-requests") || path.contains("reschedule")
                    || path.contains("scenarios") || path.endsWith(".ics") || path.equals("/api/jobs/{id}/events"));
            for (var operation : entry.getValue().entrySet()) {
                if (!Set.of("get", "post", "put", "patch", "delete").contains(operation.getKey())) continue;
                @SuppressWarnings("unchecked")
                var metadata = (Map<String, Object>) operation.getValue();
                String operationId = (String) metadata.get("operationId");
                assertNotNull(operationId, path);
                assertTrue(operationIds.add(operationId), "Duplicate operationId: " + operationId);
            }
        }
        assertEquals("SESSION", document.read("$.components.securitySchemes.sessionCookie.name"));
        assertEquals("X-XSRF-TOKEN", document.read("$.components.securitySchemes.csrfHeader.name"));
        java.util.List<String> references = document.read("$..['$ref']");
        for (String reference : references) {
            assertTrue(reference.startsWith("#/"), "Export must be self-contained: " + reference);
            Object target = document.json();
            for (String token : reference.substring(2).split("/")) {
                assertTrue(target instanceof Map<?, ?>, "Unresolved reference: " + reference);
                target = ((Map<?, ?>) target).get(token.replace("~1", "/").replace("~0", "~"));
                assertNotNull(target, "Unresolved reference: " + reference);
            }
        }
        String output = System.getProperty("slotwise.openapi.output");
        if (output != null) {
            Path file = Path.of(output);
            Files.createDirectories(file.getParent());
            Files.writeString(file, json + System.lineSeparator());
        }
    }
}
