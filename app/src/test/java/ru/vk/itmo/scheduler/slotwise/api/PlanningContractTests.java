package ru.vk.itmo.scheduler.slotwise.api;

import org.mockito.Mockito;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.BusyCalendar;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PlanningContractTests extends ApiContractTestBase {
    @Test
    void emptyCalendarCanBeSavedAndExplicitlySubmitted() throws Exception {
        mvc.perform(put("/api/sessions/1/busy-intervals/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":\"r1\",\"intervals\":[]}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/sessions/1/busy-intervals/me/submit").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":\"r1\"}"))
                .andExpect(status().isOk());
        Mockito.verify(availability).saveBusy(1L, 1L,
                new BusyCalendar.ReplaceRequest("r1", List.of()));
        Mockito.verify(availability).submit(1L, 1L,
                new BusyCalendar.SubmitRequest("r1"));
    }

    @Test
    void nonPositiveIdsAndInvalidPaginationAreClientErrors() throws Exception {
        mvc.perform(get("/api/sessions/0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/sessions").param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void durationOutsideSlotGridIsRejected() throws Exception {
        mvc.perform(put("/api/sessions/1/events/2").contentType(MediaType.APPLICATION_JSON).content("""
                        {"expectedVersion":0,"title":"Экзамен","description":null,"durationMinutes":31,"preparationDays":0,
                         "format":"ONSITE","commissionSize":0,"requiresChair":false,
                         "participants":[{"accountId":1,"role":"STUDENT"}],"equipmentIds":[],"dependencies":[]}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void malformedJsonDoesNotExposeAnInternalException() throws Exception {
        mvc.perform(post("/api/sessions").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
