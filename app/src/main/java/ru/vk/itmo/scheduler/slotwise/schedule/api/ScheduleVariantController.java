package ru.vk.itmo.scheduler.slotwise.schedule.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.vk.itmo.scheduler.slotwise.common.api.ApiPaths;
import ru.vk.itmo.scheduler.slotwise.common.api.ContractStub;
import ru.vk.itmo.scheduler.slotwise.common.api.PageResponse;
import ru.vk.itmo.scheduler.slotwise.schedule.api.dto.AssignmentCandidate;
import ru.vk.itmo.scheduler.slotwise.schedule.api.dto.AssignmentUpdateRequest;
import ru.vk.itmo.scheduler.slotwise.schedule.api.dto.AssignmentUpdateResponse;
import ru.vk.itmo.scheduler.slotwise.schedule.api.dto.PublishVariantRequest;
import ru.vk.itmo.scheduler.slotwise.schedule.api.dto.VariantResponse;
import ru.vk.itmo.scheduler.slotwise.schedule.api.dto.VariantSummaryResponse;

import java.util.List;

@RestController
@RequestMapping(produces = "application/json")
@PreAuthorize("hasRole('ORGANIZER')")
@Tag(name = "Schedule variants", description = "ORGANIZER: inspect, edit and publish variants of sessions they own")
public class ScheduleVariantController {

    @GetMapping(ApiPaths.SESSIONS + "/{id}/variants")
    @Operation(operationId = "listSessionVariants", summary = "List schedule variants with metrics",
            description = "ORGANIZER owner only. Paginated summaries include lifecycle status, input versions, "
                    + "incomplete/stale data indicators and comparable validator metrics. Page is zero-based; "
                    + "size is 1..100. An empty list is a successful result.")
    public PageResponse<VariantSummaryResponse> listSessionVariants(@PathVariable("id") @Min(1) long id,
                                                                    @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
                                                                    @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size) {
        throw ContractStub.notImplemented();
    }

    @GetMapping(ApiPaths.VARIANTS + "/{id}")
    @Operation(operationId = "getVariant", summary = "Get the complete schedule variant",
            description = "ORGANIZER owner only; inaccessible variants return 404. Returns every assignment, "
                    + "including UNASSIGNED events, both variant and assignment versions, locks, participant names, "
                    + "resources, commissions, metrics, input provenance and basic explanations. Metrics come from "
                    + "the shared validator, not a solver's self-evaluation. Explanations are limited precheck "
                    + "diagnostics and do not claim a unique cause. Stale data and missing respondents are explicit. "
                    + "FEASIBLE does not claim optimality; bound and gap are null when unknown.")
    public VariantResponse getVariant(@PathVariable("id") @Min(1) long id) {
        throw ContractStub.notImplemented();
    }

    @GetMapping(ApiPaths.VARIANTS + "/{id}/assignments/{eventId}/candidates")
    @Operation(operationId = "listAssignmentCandidates", summary = "List feasible placements for an event",
            description = "ORGANIZER owner only. Lists distinct start time and room pairs that satisfy all hard "
                    + "constraints for the event with every other assignment held fixed. Reuses the event's existing "
                    + "commission, if present. Otherwise a pair is eligible if a valid commission can be selected "
                    + "from the session pool when the event requires one. Returns an empty list if no pair is "
                    + "feasible. The query does not change the variant. Inaccessible variants and unknown events return 404.")
    public List<AssignmentCandidate> listAssignmentCandidates(@PathVariable("id") @Min(1) long id,
                                                               @PathVariable("eventId") @Min(1) long eventId) {
        throw ContractStub.notImplemented();
    }

    @PatchMapping(ApiPaths.VARIANTS + "/{id}/assignments/{eventId}")
    @Operation(operationId = "updateAssignment", summary = "Validate or save an assignment and its lock",
            description = "ORGANIZER owner only, DRAFT variants only. Supplies the complete editable assignment, "
                    + "including explicit locked and commissionMembers, and checks expectedVariantVersion and "
                    + "expectedAssignmentVersion; concurrent changes or an immutable variant return 409. ASSIGNED "
                    + "requires start/end, the selected physical room or online platform, and a valid commission. "
                    + "Times are UTC instants on 5-minute boundaries, end follows start and duration matches the "
                    + "event. UNASSIGNED clears times/resource/commission. The same domain validator checks H1-H9 "
                    + "and recomputes (U, P) with S1-S5 and preparation days H10: hard violations return 422 with issues and never persist. dryRun=true "
                    + "returns a preview, metrics and affected account IDs without changing either version. Saving "
                    + "atomically changes the assignment and variant versions. Locking enforces H9 in later solves; "
                    + "it does not publish. A conflicting lock is never silently removed.")
    public AssignmentUpdateResponse updateAssignment(@PathVariable("id") @Min(1) long id,
                                                     @PathVariable("eventId") @Min(1) long eventId,
                                                     @RequestParam(name = "dryRun", defaultValue = "false") boolean dryRun,
                                                     @Valid @RequestBody AssignmentUpdateRequest request) {
        throw ContractStub.notImplemented();
    }

    @PostMapping(ApiPaths.VARIANTS + "/{id}/publish")
    @Operation(operationId = "publishVariant", summary = "Publish the whole schedule variant",
            description = "ORGANIZER owner only. Publishes the entire validated DRAFT variant atomically and marks "
                    + "the previous publication SUPERSEDED. No subset of assignments can be selected. Checks "
                    + "expectedVersion (409 on a concurrent edit). Hard violations block publication with 422, "
                    + "regardless of confirmation flags. confirmUnassigned and confirmStaleData must both be "
                    + "explicitly supplied; each must be true when the corresponding condition exists, otherwise "
                    + "422 reports the required confirmation. Unassigned events remain visible to the organizer. "
                    + "Repeating publication of the current published variant has no additional effect. "
                    + "Participants see only the current publication; published assignments cannot be edited.")
    public VariantResponse publishVariant(@PathVariable("id") @Min(1) long id,
                                          @Valid @RequestBody PublishVariantRequest request) {
        throw ContractStub.notImplemented();
    }
}
