package ru.vk.itmo.scheduler.slotwise.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlanningModelContractTests extends ApiContractTestBase {

    private static final String TEMPLATE = """
            {"name":"Экзамен","defaultDurationMinutes":20,"granularity":"PER_GROUP","defaultFormat":"ONSITE",
             "commissionSize":0,"requiresChair":false,"active":true,"preparationDays":2,
             "participantRoles":["STUDENT"],"equipmentIds":[],
             "dependencies":[{"predecessorTypeId":1,"minGapDays":1,"maxGapDays":2}]}
            """;
    private static final String RULES = """
            {"expectedVersion":0,"weights":{"teacherGaps":1,"commissionLoadSpread":2,"undesirableTime":3,
             "teacherRoomChanges":4,"movedEvents":5},"roomChangeBufferMinutes":5,
             "defaultAlgorithm":"CP_SAT","timeLimitSeconds":10,"seed":1}
            """;

    @ParameterizedTest
    @ValueSource(ints = {5, 20, 25, 30})
    void acceptsFiveMinuteGridAndPreparationDays(int minutes) throws Exception {
        mvc.perform(post("/api/event-types").contentType(MediaType.APPLICATION_JSON)
                        .content(TEMPLATE.replace("\"defaultDurationMinutes\":20", "\"defaultDurationMinutes\":" + minutes)))
                .andExpect(status().isNotImplemented());
    }

    @Test
    void acceptsExactlyTheFiveIntegerWeightsAndFiveMinuteBuffer() throws Exception {
        mvc.perform(put("/api/sessions/1/rules").contentType(MediaType.APPLICATION_JSON).content(RULES))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsFractionalWeightInsteadOfCoercingItToAnInteger() throws Exception {
        String body = RULES.replace("\"teacherGaps\":1", "\"teacherGaps\":0.5")
                .replace("\"movedEvents\":5", "\"movedEvents\":5,\"teacherLoadSpread\":1,"
                        + "\"commissionSplits\":1,\"inconvenientLocalTime\":1,\"nonPreferredBuilding\":1")
                .replace("\"roomChangeBufferMinutes\":5", "\"roomChangeBufferMinutes\":15");
        mvc.perform(put("/api/sessions/1/rules").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMaximumGapBelowMinimum() throws Exception {
        mvc.perform(post("/api/event-types").contentType(MediaType.APPLICATION_JSON)
                        .content(TEMPLATE.replace("\"defaultDurationMinutes\":20", "\"defaultDurationMinutes\":30")
                                .replace("\"maxGapDays\":2", "\"maxGapDays\":0")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsNegativePreparationDays() throws Exception {
        mvc.perform(post("/api/event-types").contentType(MediaType.APPLICATION_JSON)
                        .content(TEMPLATE.replace("\"preparationDays\":2", "\"preparationDays\":-1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresNonEmptyCommissionForAChair() throws Exception {
        String template = TEMPLATE.replace("\"requiresChair\":false", "\"requiresChair\":true");
        mvc.perform(post("/api/event-types").contentType(MediaType.APPLICATION_JSON).content(template))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'commissionValid')]").isNotEmpty());
        mvc.perform(put("/api/event-types/1").contentType(MediaType.APPLICATION_JSON)
                        .content(template.replaceFirst("\\{", "{\"expectedVersion\":0,")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'commissionValid')]").isNotEmpty());
    }

    @Test
    void supportsAnUnboundedDependencyAndValidatesManualEventDependencies() throws Exception {
        mvc.perform(post("/api/event-types").contentType(MediaType.APPLICATION_JSON)
                        .content(TEMPLATE.replace("\"maxGapDays\":2", "\"maxGapDays\":null")))
                .andExpect(status().isNotImplemented());
        String event = """
                {"expectedVersion":0,"title":"Экзамен","description":null,"durationMinutes":20,
                 "format":"ONSITE","preparationDays":2,"commissionSize":0,"requiresChair":false,
                 "participants":[{"accountId":1,"role":"STUDENT"}],"equipmentIds":[],
                 "dependencies":[{"predecessorEventId":1,"minGapDays":1,"maxGapDays":2}]}
                """;
        mvc.perform(put("/api/sessions/1/events/2").contentType(MediaType.APPLICATION_JSON).content(event))
                .andExpect(status().isNotImplemented());
        mvc.perform(put("/api/sessions/1/events/2").contentType(MediaType.APPLICATION_JSON)
                        .content(event.replace("\"requiresChair\":false", "\"requiresChair\":true")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'commissionValid')]").isNotEmpty());
        mvc.perform(put("/api/sessions/1/events/2").contentType(MediaType.APPLICATION_JSON)
                        .content(event.replace("\"maxGapDays\":2", "\"maxGapDays\":0")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exportsNewEventInputsAndLexicographicMetrics() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.CreateEventTypeRequest.required", hasItems("preparationDays")))
                .andExpect(jsonPath("$.components.schemas.EventResponse.required", hasItems("preparationDays", "granularity")))
                .andExpect(jsonPath("$.components.schemas.EventTypeDependency.properties.maxGapDays").exists())
                .andExpect(jsonPath("$.components.schemas.EventPredecessor.properties.maxGapDays").exists())
                .andExpect(jsonPath("$.components.schemas.SessionRulesResponse.required", hasItems("slotMinutes")))
                .andExpect(jsonPath("$.components.schemas.ScheduleMetrics.required",
                        hasItems("unassignedEvents", "penalty", "teacherRoomChanges", "commissionLoadRangeHours",
                                "commissionLoadStandardDeviationHours")))
                .andExpect(jsonPath("$.components.schemas.ScheduleMetrics.properties.objective").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ScheduleMetrics.properties.unassignedPenalty").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.Weights.properties.inconvenientLocalTime").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.Weights.properties.nonPreferredBuilding").doesNotExist());
    }
}
