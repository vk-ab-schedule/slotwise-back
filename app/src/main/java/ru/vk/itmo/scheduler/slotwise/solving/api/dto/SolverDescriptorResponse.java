package ru.vk.itmo.scheduler.slotwise.solving.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import ru.vk.itmo.scheduler.slotwise.solving.SolverAlgorithm;

public record SolverDescriptorResponse(
        @NotNull SolverAlgorithm algorithm,
        @NotNull String name,
        @NotNull String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean available,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Whether the algorithm can report a proven soft penalty P bound and gap at the reported U; "
                        + "these do not prove minimum U. OPTIMAL requires proof of both stages.") boolean supportsBestBound) {
}
