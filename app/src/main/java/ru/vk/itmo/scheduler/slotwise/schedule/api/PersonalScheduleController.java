package ru.vk.itmo.scheduler.slotwise.schedule.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.vk.itmo.scheduler.slotwise.common.api.ApiPaths;
import ru.vk.itmo.scheduler.slotwise.common.api.ContractStub;
import ru.vk.itmo.scheduler.slotwise.schedule.api.dto.PersonalScheduleResponse;

import java.time.Instant;

@RestController
@RequestMapping(value = ApiPaths.MY_SCHEDULE, produces = "application/json")
@Tag(name = "Personal schedule", description = "STUDENT or TEACHER: their own currently published assignments")
public class PersonalScheduleController {

    @GetMapping
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
    @Operation(operationId = "getPersonalSchedule", summary = "Get my published calendar",
            description = "STUDENT or TEACHER. Identity comes exclusively from the authenticated session; no user "
                    + "override exists. Requires UTC instant boundaries from and to, with from < to; selects events "
                    + "overlapping [from, to) from current published variants across participating sessions. "
                    + "Includes commission participation, changed-event markers, event format, resource and "
                    + "participant names with roles in the event. Times are instants, displayed in the returned "
                    + "user timeZone. Exposes no other person's private availability or credentials. Drafts, "
                    + "superseded variants and unassigned events are excluded. No assignments yields an empty "
                    + "events list, not an error.")
    public PersonalScheduleResponse getPersonalSchedule(@RequestParam("from") Instant from,
                                                        @RequestParam("to") Instant to) {
        throw ContractStub.notImplemented();
    }
}
