package ru.vk.itmo.scheduler.slotwise.planning.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.vk.itmo.scheduler.slotwise.auth.AccountPrincipal;
import ru.vk.itmo.scheduler.slotwise.common.api.ApiPaths;
import ru.vk.itmo.scheduler.slotwise.planning.PlanningAvailabilityService;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.ChangeCollectionRequest;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.CollectionResponse;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.SessionVersionRequest;

@PreAuthorize("hasRole('ORGANIZER')")
@RequiredArgsConstructor
@RestController
@RequestMapping(value = ApiPaths.SESSIONS + "/{id}/collection", produces = "application/json")
@Tag(name = "Availability collection", description = "ORGANIZER owner; status only, no private busy intervals of others")
public class CollectionController {
    private final PlanningAvailabilityService service;

    @GetMapping()
    @Operation(operationId = "getCollection", summary = "Состояние сбора занятости", description = "Shows answered/non-answered people and no-free-window warnings; never exposes private intervals.")
    public CollectionResponse getCollection(@PathVariable("id") @Min(1) long id, @AuthenticationPrincipal AccountPrincipal user) {
        return service.collection(id, user.getAccountId());
    }

    @PostMapping("/close")
    @Operation(operationId = "closeCollection", summary = "Закрыть сбор досрочно", description = "Manual close preserves answers. Does not launch solving automatically in MVP (D-15).")
    public CollectionResponse closeCollection(@PathVariable("id") @Min(1) long id, @Valid @RequestBody SessionVersionRequest request, @AuthenticationPrincipal AccountPrincipal user) {
        return service.close(id, user.getAccountId(), request);
    }
    @PatchMapping
    @Operation(operationId = "changeCollectionDeadline", summary = "Изменить дедлайн сбора",
            description = "Owner only; preserves answers. Deadline must be in the future and before the session starts. Closed collection cannot be reopened.")
    public CollectionResponse changeCollectionDeadline(@PathVariable("id") @Min(1) long id,
            @Valid @RequestBody ChangeCollectionRequest request,
            @AuthenticationPrincipal AccountPrincipal user) {
        return service.changeCollection(id, user.getAccountId(), request);
    }
}

