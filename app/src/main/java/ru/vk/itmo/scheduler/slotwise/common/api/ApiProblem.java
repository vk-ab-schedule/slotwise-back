package ru.vk.itmo.scheduler.slotwise.common.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.net.URI;
import java.util.List;

/**
 * Documents the ProblemDetail extensions emitted by the exception handler.
 */
public record ApiProblem(
        @NotNull URI type,
        @NotNull String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "400", maximum = "599") int status,
        @NotNull String detail,
        @NotNull @Schema(type = "string", format = "uri-reference") URI instance,
        @NotNull String code,
        @Schema(description = "Present for field validation errors") List<FieldViolation> fieldErrors,
        @Schema(description = "Present when a domain validator supplies diagnostics") List<ApiIssue> issues) {
}
