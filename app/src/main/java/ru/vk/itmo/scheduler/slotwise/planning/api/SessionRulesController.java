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
import ru.vk.itmo.scheduler.slotwise.common.api.ContractStub;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.SessionRules;

@PreAuthorize("hasRole('ORGANIZER')")
@RestController
@RequestMapping(value = ApiPaths.SESSIONS + "/{id}/rules", produces = "application/json")
@Tag(name = "Session rules", description = "ORGANIZER owner; hard constraints are server controlled")
public class SessionRulesController {

    @GetMapping()
    @Operation(operationId = "getSessionRules", summary = "Получить правила и веса", description = "Includes defaults and current session/input versions.")
    public SessionRules.Response getSessionRules(@PathVariable("id") @Min(1) long id) {
        throw ContractStub.notImplemented();
    }

    @PutMapping()
    @Operation(operationId = "replaceSessionRules", summary = "Настроить правила", description = "Replace the five integer S1-S5 weights and defaults for future solves. Time penalties use minutes; saved variants retain their immutable input snapshot. The model receives the slot length as a parameter (currently 5 minutes).")
    public SessionRules.Response replaceSessionRules(@PathVariable("id") @Min(1) long id, @Valid @RequestBody SessionRules.UpdateRequest request) {
        throw ContractStub.notImplemented();
    }
}

