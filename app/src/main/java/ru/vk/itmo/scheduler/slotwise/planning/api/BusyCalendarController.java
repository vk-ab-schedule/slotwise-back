package ru.vk.itmo.scheduler.slotwise.planning.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.vk.itmo.scheduler.slotwise.auth.AccountPrincipal;
import ru.vk.itmo.scheduler.slotwise.common.api.ApiPaths;
import ru.vk.itmo.scheduler.slotwise.planning.PlanningAvailabilityService;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.BusyCalendar;

@PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
@RequiredArgsConstructor
@RestController
@RequestMapping(value = ApiPaths.SESSIONS + "/{id}/busy-intervals/me", produces = "application/json")
@Tag(name = "Own availability", description = "Session participant; authenticated user is always the calendar owner")
public class BusyCalendarController {
    private final PlanningAvailabilityService service;
    @GetMapping()
    @Operation(operationId = "getMyBusyCalendar", summary = "Моя занятость", description = "Known timetable is read-only and always hard. Personal CRITICAL availability is hard for teachers and individual student events; for group events student CRITICAL availability is soft with coefficient 10 (D-16). Missing timetable produces a warning. No accountId override.")
    public BusyCalendar.Response getMyBusyCalendar(@PathVariable("id") @Min(1) long id, @AuthenticationPrincipal AccountPrincipal user) {
        return service.busy(id, user.getAccountId());
    }

    @PutMapping()
    @Operation(operationId = "replaceMyBusyCalendar", summary = "Сохранить занятые интервалы", description = "Full replacement guarded by revision; [] is valid. Round busy intervals outward to 5 minutes, clip to session hours, merge overlaps with highest importance. Saving and submitting are distinct.")
    public BusyCalendar.Response replaceMyBusyCalendar(@PathVariable("id") @Min(1) long id, @Valid @RequestBody BusyCalendar.ReplaceRequest request, @AuthenticationPrincipal AccountPrincipal user) {
        return service.saveBusy(id, user.getAccountId(), request);
    }

    @PostMapping("/submit")
    @Operation(operationId = "submitMyBusyCalendar", summary = "Отправить ответ о занятости", description = "An empty calendar explicitly means no restrictions. Requires open collection and current revision; unmarked working time is free.")
    public BusyCalendar.Response submitMyBusyCalendar(@PathVariable("id") @Min(1) long id, @Valid @RequestBody BusyCalendar.SubmitRequest request, @AuthenticationPrincipal AccountPrincipal user) {
        return service.submit(id, user.getAccountId(), request);
    }
}
