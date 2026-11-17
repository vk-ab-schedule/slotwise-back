package ru.vk.itmo.scheduler.slotwise.planning.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.vk.itmo.scheduler.slotwise.common.api.ApiPaths;
import ru.vk.itmo.scheduler.slotwise.common.api.ContractStub;
import ru.vk.itmo.scheduler.slotwise.common.api.PageResponse;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.CreateEventsRequest;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.CreateEventsResponse;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.EventResponse;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.UpdateEventRequest;

@PreAuthorize("hasRole('ORGANIZER')")
@RestController
@RequestMapping(value = ApiPaths.SESSIONS + "/{id}/events", produces = "application/json")
@Tag(name = "Planning events", description = "ORGANIZER owner; events expand templates but carry independent editable requirements")
public class PlanningEventController {

    @GetMapping()
    @Operation(operationId = "listSessionEvents", summary = "События сессии", description = "Includes incomplete events and their completion state.")
    public PageResponse<EventResponse> listSessionEvents(@PathVariable("id") @Min(1) long id,
                                                         @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
                                                         @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        throw ContractStub.notImplemented();
    }

    @PostMapping()
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createSessionEvents", summary = "Создать события по шаблону", description = "Expand by template granularity; infer teachers/supervisors; report missing links and deduplicate students. Incomplete events do not enter solving.")
    public CreateEventsResponse createSessionEvents(@PathVariable("id") @Min(1) long id, @Valid @RequestBody CreateEventsRequest request) {
        throw ContractStub.notImplemented();
    }

    @GetMapping("/{eventId}")
    @Operation(operationId = "getSessionEvent", summary = "Получить событие", description = "ORGANIZER owner.")
    public EventResponse getSessionEvent(@PathVariable("id") @Min(1) long id, @PathVariable("eventId") @Min(1) long eventId) {
        throw ContractStub.notImplemented();
    }

    @PutMapping("/{eventId}")
    @Operation(operationId = "updateSessionEvent", summary = "Изменить событие и зависимости", description = "Replaces the editable form; template changes never modify existing events. Cyclic dependencies are rejected.")
    public EventResponse updateSessionEvent(@PathVariable("id") @Min(1) long id,
                                            @PathVariable("eventId") @Min(1) long eventId,
                                            @Valid @RequestBody UpdateEventRequest request) {
        throw ContractStub.notImplemented();
    }

    @DeleteMapping("/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteSessionEvent", summary = "Удалить событие", description = "ORGANIZER owner; reject deletion if referenced by a saved schedule. Detect stale version.")
    public void deleteSessionEvent(@PathVariable("id") @Min(1) long id,
                                   @PathVariable("eventId") @Min(1) long eventId,
                                   @RequestParam("expectedVersion") @PositiveOrZero long expectedVersion) {
        throw ContractStub.notImplemented();
    }
}

