package ru.vk.itmo.scheduler.slotwise.solving.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.vk.itmo.scheduler.slotwise.auth.AccountPrincipal;
import ru.vk.itmo.scheduler.slotwise.common.api.ApiPaths;
import ru.vk.itmo.scheduler.slotwise.solving.SolvingService;
import ru.vk.itmo.scheduler.slotwise.solving.api.dto.PrecheckRequest;
import ru.vk.itmo.scheduler.slotwise.solving.api.dto.PrecheckResponse;
import ru.vk.itmo.scheduler.slotwise.solving.api.dto.SolveRequest;
import ru.vk.itmo.scheduler.slotwise.solving.api.dto.SolverDescriptorResponse;
import ru.vk.itmo.scheduler.slotwise.solving.api.dto.SolverJobResponse;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(produces = "application/json")
@PreAuthorize("hasRole('ORGANIZER')")
@Tag(name = "Solving", description = "ORGANIZER: manual solving and polling for sessions they own")
public class SolvingController {
    private final SolvingService solving;

    @GetMapping(ApiPaths.SOLVERS)
    @Operation(operationId = "listSolvers", summary = "List solver capabilities",
            description = "ORGANIZER. Lists known algorithms and their availability on this deployment; "
                    + "an unavailable algorithm cannot be launched. No calculation is started.")
    public List<SolverDescriptorResponse> listSolvers() {
        return solving.solvers();
    }

    @PostMapping(ApiPaths.SESSIONS + "/{id}/precheck")
    @Operation(operationId = "precheckSession", summary = "Check planning input before solving",
            description = "ORGANIZER owner only. Checks expectedInputVersion against the current session input "
                    + "version (409 STALE_INPUT_VERSION on mismatch), then reports basic problems linked to events, "
                    + "people and rooms: the M6 prechecks, INCOMPLETE_EVENTS that do not take part in solving and "
                    + "NOT_RESPONDED participants. Missing responses are warnings: those people remain available during working hours except "
                    + "for known timetable commitments. Problems do not prohibit a subsequent solve. "
                    + "Does not modify data or start a job; diagnostics do not prove infeasibility.")
    public PrecheckResponse precheckSession(@PathVariable("id") @Min(1) long id,
                                            @Valid @RequestBody PrecheckRequest request,
                                            @AuthenticationPrincipal AccountPrincipal organizer) {
        return solving.precheck(id, organizer.getAccountId(), request);
    }

    @PostMapping(ApiPaths.SESSIONS + "/{id}/solve")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(operationId = "solveSession", summary = "Queue a manual calculation",
            description = "ORGANIZER owner only. Snapshots input at expectedInputVersion; a version mismatch is 409 "
                    + "STALE_INPUT_VERSION, an unavailable algorithm is 409 SOLVER_UNAVAILABLE. May run before collection closes; such results are marked incomplete and retain missing "
                    + "respondents. Reuses an identical request for the same session, input version, algorithm and "
                    + "parameters; if the session already has an active calculation, returns that job. Otherwise "
                    + "queues a job with its queue position. Any signed seed is valid. Optional baseVariantId must "
                    + "belong to this session (404 VARIANT_NOT_FOUND); its assignments provide the baseline for S5 and its "
                    + "locks enforce H9 and stay locked in the new draft. "
                    + "Poll getSolverJob for progress and best known quality (unassigned events, then penalty). Completion creates a validated draft when a solution exists; "
                    + "it never publishes automatically. A time limit without a solution is NO_SOLUTION, "
                    + "not a proof of infeasibility or a technical failure.")
    public SolverJobResponse solveSession(@PathVariable("id") @Min(1) long id,
                                          @Valid @RequestBody SolveRequest request,
                                          @AuthenticationPrincipal AccountPrincipal organizer) {
        return solving.solve(id, organizer.getAccountId(), request);
    }

    @GetMapping(ApiPaths.JOBS + "/{id}")
    @Operation(operationId = "getSolverJob", summary = "Poll calculation status and progress",
            description = "ORGANIZER owner of the job's session only; inaccessible jobs return 404. "
                    + "QUEUED, RUNNING, COMPLETED, FAILED and CANCELLED describe execution, while solveStatus "
                    + "describes the solver result. COMPLETED may have NO_SOLUTION and a null resultId; only FAILED "
                    + "denotes a technical error. A completed feasible or optimal result links to a draft variant. "
                    + "Unavailable progress, quality, queue position, error, result and timestamps are explicit nulls. "
                    + "Progress is the elapsed share of the time limit. Error codes: SOLVER_FAILED, and INTERRUPTED "
                    + "for jobs left unfinished by a server restart; both may be launched again.")
    public SolverJobResponse getSolverJob(@PathVariable("id") @Min(1) long id,
                                          @AuthenticationPrincipal AccountPrincipal organizer) {
        return solving.job(id, organizer.getAccountId());
    }
}
