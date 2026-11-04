package ru.vk.itmo.scheduler.slotwise.planning.api;

import jakarta.validation.constraints.Min;
import ru.vk.itmo.scheduler.slotwise.common.api.ApiPaths;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.vk.itmo.scheduler.slotwise.common.api.ContractStub;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.CollectionResponse;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.SessionVersionRequest;

@PreAuthorize("hasRole('ORGANIZER')")
@RestController
@RequestMapping(value = ApiPaths.SESSIONS + "/{id}/collection", produces = "application/json")
@Tag(name = "Availability collection", description = "ORGANIZER owner; status only, no private busy intervals of others")
public class CollectionController {

    @GetMapping()
    @Operation(operationId = "getCollection", summary = "Состояние сбора занятости", description = "Shows answered/non-answered people and no-free-window warnings; never exposes private intervals.")
    public CollectionResponse getCollection(@PathVariable("id") @Min(1) long id) {
        throw ContractStub.notImplemented();
    }

    @PostMapping("/close")
    @Operation(operationId = "closeCollection", summary = "Закрыть сбор досрочно", description = "Manual close preserves answers. Does not launch solving automatically in MVP (D-15).")
    public CollectionResponse closeCollection(@PathVariable("id") @Min(1) long id, @Valid @RequestBody SessionVersionRequest request) {
        throw ContractStub.notImplemented();
    }
}

