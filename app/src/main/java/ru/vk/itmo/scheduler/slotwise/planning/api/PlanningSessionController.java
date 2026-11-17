package ru.vk.itmo.scheduler.slotwise.planning.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.CreatePlanningSessionRequest;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.PlanningSessionResponse;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.SessionSelection;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.UpdatePlanningSessionRequest;

@RestController
@RequestMapping(value = ApiPaths.SESSIONS, produces = "application/json")
@Tag(name = "Planning sessions", description = "ORGANIZER owns sessions; participants only read their available sessions")
public class PlanningSessionController {

    @GetMapping()
    @Operation(operationId = "listSessions", summary = "Список доступных сессий", description = "Owner sees own sessions; student/teacher sees only sessions they participate in.")
    public PageResponse<PlanningSessionResponse> listSessions(@RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
                                                              @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        throw ContractStub.notImplemented();
    }

    @PreAuthorize("hasRole('ORGANIZER')")
    @PostMapping()
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createSession", summary = "Создать сессию", description = "ORGANIZER. Collection window ends before the session starts; name is unique per owner.")
    public PlanningSessionResponse createSession(@Valid @RequestBody CreatePlanningSessionRequest request) {
        throw ContractStub.notImplemented();
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getSession", summary = "Получить сессию", description = "Owner or participant. Timings are UTC; local working hours use session timeZone.")
    public PlanningSessionResponse getSession(@PathVariable("id") @Min(1) long id) {
        throw ContractStub.notImplemented();
    }

    @PreAuthorize("hasRole('ORGANIZER')")
    @PatchMapping("/{id}")
    @Operation(operationId = "updateSession", summary = "Изменить параметры сессии", description = "ORGANIZER owner. Replaces editable settings. Window changes preserve submitted answers; conflicts return 409.")
    public PlanningSessionResponse updateSession(@PathVariable("id") @Min(1) long id, @Valid @RequestBody UpdatePlanningSessionRequest request) {
        throw ContractStub.notImplemented();
    }

    @PreAuthorize("hasRole('ORGANIZER')")
    @GetMapping("/{id}/participants")
    @Operation(operationId = "getSessionParticipants", summary = "Участники сессии", description = "ORGANIZER owner. Resolved people include participants inferred from events and commission.")
    public SessionSelection.ParticipantsResponse getSessionParticipants(@PathVariable("id") @Min(1) long id) {
        throw ContractStub.notImplemented();
    }

    @PreAuthorize("hasRole('ORGANIZER')")
    @PutMapping("/{id}/participants")
    @Operation(operationId = "replaceSessionParticipants", summary = "Выбрать группы и пользователей", description = "ORGANIZER owner. Replace selection, deduplicate; removing people used by events is rejected.")
    public SessionSelection.ParticipantsResponse replaceSessionParticipants(@PathVariable("id") @Min(1) long id, @Valid @RequestBody SessionSelection.ParticipantsRequest request) {
        throw ContractStub.notImplemented();
    }

    @PreAuthorize("hasRole('ORGANIZER')")
    @GetMapping("/{id}/commission-pool")
    @Operation(operationId = "getCommissionPool", summary = "Пул комиссии", description = "ORGANIZER owner.")
    public SessionSelection.CommissionPoolResponse getCommissionPool(@PathVariable("id") @Min(1) long id) {
        throw ContractStub.notImplemented();
    }

    @PreAuthorize("hasRole('ORGANIZER')")
    @PutMapping("/{id}/commission-pool")
    @Operation(operationId = "replaceCommissionPool", summary = "Изменить пул комиссии", description = "ORGANIZER owner. Teachers only; marks potential chairpersons. Increments session input version.")
    public SessionSelection.CommissionPoolResponse replaceCommissionPool(@PathVariable("id") @Min(1) long id, @Valid @RequestBody SessionSelection.CommissionPoolRequest request) {
        throw ContractStub.notImplemented();
    }

    @PreAuthorize("hasRole('ORGANIZER')")
    @GetMapping("/{id}/rooms")
    @Operation(operationId = "getSessionRooms", summary = "Ресурсы сессии", description = "ORGANIZER owner.")
    public SessionSelection.RoomsResponse getSessionRooms(@PathVariable("id") @Min(1) long id) {
        throw ContractStub.notImplemented();
    }

    @PreAuthorize("hasRole('ORGANIZER')")
    @PutMapping("/{id}/rooms")
    @Operation(operationId = "replaceSessionRooms", summary = "Выбрать аудитории и онлайн-площадку", description = "ORGANIZER owner. Resources come from the university catalog; no inline creation.")
    public SessionSelection.RoomsResponse replaceSessionRooms(@PathVariable("id") @Min(1) long id, @Valid @RequestBody SessionSelection.RoomsRequest request) {
        throw ContractStub.notImplemented();
    }
}

