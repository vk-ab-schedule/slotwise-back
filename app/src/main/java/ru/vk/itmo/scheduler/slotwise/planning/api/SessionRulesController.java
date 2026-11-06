package ru.vk.itmo.scheduler.slotwise.planning.api;

import jakarta.validation.constraints.Min;
import ru.vk.itmo.scheduler.slotwise.common.api.ApiPaths;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import ru.vk.itmo.scheduler.slotwise.auth.AccountPrincipal;
import ru.vk.itmo.scheduler.slotwise.planning.PlanningAvailabilityFacade;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.SessionRules;

@PreAuthorize("hasRole('ORGANIZER')")
@RequiredArgsConstructor
@RestController
@RequestMapping(value = ApiPaths.SESSIONS + "/{id}/rules", produces = "application/json")
@Tag(name = "Session rules", description = "ORGANIZER owner; hard constraints are server controlled")
public class SessionRulesController {
    private final PlanningAvailabilityFacade service;

    @GetMapping()
    @Operation(operationId = "getSessionRules", summary = "Получить правила и веса", description = "Includes defaults and current session/input versions.")
    public SessionRules.Response getSessionRules(@PathVariable("id") @Min(1) long id, @AuthenticationPrincipal AccountPrincipal user) {
        return service.rules(id, user.getAccountId());
    }

    @PutMapping()
    @Operation(operationId = "replaceSessionRules", summary = "Настроить правила", description = "Replace the five integer S1-S5 weights and defaults for future solves. Time penalties use minutes; saved variants retain their immutable input snapshot. The model receives the slot length as a parameter (currently 5 minutes).")
    public SessionRules.Response replaceSessionRules(@PathVariable("id") @Min(1) long id, @Valid @RequestBody SessionRules.UpdateRequest request, @AuthenticationPrincipal AccountPrincipal user) {
        return service.saveRules(id, user.getAccountId(), request);
    }
}

