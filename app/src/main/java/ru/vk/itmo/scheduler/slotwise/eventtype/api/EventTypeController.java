package ru.vk.itmo.scheduler.slotwise.eventtype.api;

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
import ru.vk.itmo.scheduler.slotwise.eventtype.api.dto.CreateEventTypeRequest;
import ru.vk.itmo.scheduler.slotwise.eventtype.api.dto.EventTypeResponse;
import ru.vk.itmo.scheduler.slotwise.eventtype.api.dto.UpdateEventTypeRequest;

@RestController
@RequestMapping(value = ApiPaths.EVENT_TYPES, produces = "application/json")
@Tag(name = "Event types", description = "System-wide templates maintained by ADMIN and available to ORGANIZER")
public class EventTypeController {

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    @Operation(operationId = "listEventTypes", summary = "Find event templates",
            description = "ADMIN or ORGANIZER. Filter by name and active status; active templates can be used for new events.")
    public PageResponse<EventTypeResponse> listEventTypes(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "active", required = false) Boolean active,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        throw ContractStub.notImplemented();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "createEventType", summary = "Create an event template",
            description = "ADMIN. Defines default participants, commission, equipment and predecessor types. "
                    + "Dependencies must reference existing types and must not form cycles.")
    public EventTypeResponse createEventType(@Valid @RequestBody CreateEventTypeRequest request) {
        throw ContractStub.notImplemented();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    @Operation(operationId = "getEventType", summary = "Get an event template", description = "ADMIN or ORGANIZER.")
    public EventTypeResponse getEventType(@PathVariable("id") @Min(1) long id) {
        throw ContractStub.notImplemented();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "updateEventType", summary = "Replace an event template",
            description = "ADMIN. Replaces template fields and relations. Existing events retain their copied values. "
                    + "Cyclic dependencies are rejected; a stale version returns 409.")
    public EventTypeResponse updateEventType(@PathVariable("id") @Min(1) long id,
                                             @Valid @RequestBody UpdateEventTypeRequest request) {
        throw ContractStub.notImplemented();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(operationId = "deleteEventType", summary = "Delete an unused event template",
            description = "ADMIN. A stale version or a template referenced by events returns 409; "
                    + "set active=false to hide a template that is in use.")
    public void deleteEventType(@PathVariable("id") @Min(1) long id,
                                @RequestParam("expectedVersion") @PositiveOrZero long expectedVersion) {
        throw ContractStub.notImplemented();
    }
}
