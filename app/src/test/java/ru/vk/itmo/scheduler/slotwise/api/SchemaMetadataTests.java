package ru.vk.itmo.scheduler.slotwise.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "springdoc.cache.disabled=true")
class SchemaMetadataTests extends ApiContractTestBase {
    @Test
    void generatesConstrainedParametersWithoutSchemaCloningWarnings(CapturedOutput output) throws Exception {
        // Regenerate on every request so a cached document cannot hide cloning failures.
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.openapi").value("3.1.0"))
                .andExpect(jsonPath("$.paths['/api/admin/accounts'].get.parameters[?(@.name == 'page')].schema.minimum", hasItem(0)))
                .andExpect(jsonPath("$.paths['/api/admin/accounts'].get.parameters[?(@.name == 'page')].schema.maximum").isEmpty())
                .andExpect(jsonPath("$.paths['/api/admin/accounts'].get.parameters[?(@.name == 'size')].schema.minimum", hasItem(1)))
                .andExpect(jsonPath("$.paths['/api/admin/accounts'].get.parameters[?(@.name == 'size')].schema.maximum", hasItem(100)))
                .andExpect(jsonPath("$.paths['/api/admin/accounts/{id}'].get.parameters[?(@.name == 'id')].schema.minimum", hasItem(1)))
                .andExpect(jsonPath("$.paths['/api/admin/accounts/{id}'].get.parameters[?(@.name == 'id')].schema.maximum").isEmpty());

        assertThat(output.getAll()).doesNotContain("Json Processing Exception occurred");
    }

    @Test
    void schemaMatchesMvcBoundariesAndJsonFormats() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.SolveRequest.properties.timeLimitSeconds.minimum").value(1))
                .andExpect(jsonPath("$.components.schemas.SolveRequest.properties.baseVariantId.minimum").value(1))
                .andExpect(jsonPath("$.components.schemas.UpdateUniversityRoomRequest.properties.capacity.type", hasItem("null")))
                .andExpect(jsonPath("$.components.schemas.UpdateUniversityRoomRequest.properties.building.type", hasItem("null")))
                .andExpect(jsonPath("$.components.schemas.UpdateUniversityRoomRequest.properties.externalId.type", hasItem("null")))
                .andExpect(jsonPath("$.components.schemas.CreatePlanningSessionRequest.properties.workingDayStart.format", not("time")))
                .andExpect(jsonPath("$.components.schemas.CreatePlanningSessionRequest.properties.workingDayStart.pattern").exists())
                .andExpect(jsonPath("$.components.schemas.ApiProblem.properties.instance.format").value("uri-reference"));
    }
}
