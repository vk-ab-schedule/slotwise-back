package ru.vk.itmo.scheduler.slotwise.solving.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.vk.itmo.scheduler.slotwise.common.api.ApiPaths;
import ru.vk.itmo.scheduler.slotwise.common.api.ContractStub;
import ru.vk.itmo.scheduler.slotwise.solving.api.dto.PrecheckRequest;
import ru.vk.itmo.scheduler.slotwise.solving.api.dto.PrecheckResponse;
import ru.vk.itmo.scheduler.slotwise.solving.api.dto.SolveRequest;
import ru.vk.itmo.scheduler.slotwise.solving.api.dto.SolverDescriptorResponse;
import ru.vk.itmo.scheduler.slotwise.solving.api.dto.SolverJobResponse;

import java.util.List;

@RestController
@RequestMapping(produces = "application/json")
@PreAuthorize("hasRole('ORGANIZER')")
@Tag(name = "Solving", description = "ORGANIZER: manual solving and polling for sessions they own")
public class SolvingController {

    @GetMapping(ApiPaths.SOLVERS)
    @Operation(operationId = "listSolvers", summary = "List solver capabilities",
            description = "ORGANIZER. Lists known algorithms and their availability on this deployment; "
                    + "an unavailable algorithm cannot be launched. No calculation is started.")
    public List<SolverDescriptorResponse> listSolvers() {
        throw ContractStub.notImplemented();
    }

    @PostMapping(ApiPaths.SESSIONS + "/{id}/precheck")
    @Operation(operationId = "precheckSession", summary = "Check planning input before solving",
            description = "ORGANIZER owner only. Checks expectedInputVersion against the current session input "
                    + "version (409 on mismatch), then reports basic problems linked to events, people and rooms. "
                    + "Missing responses are warnings: those people remain available during working hours except "
                    + "for known timetable commitments. Problems do not prohibit a subsequent solve. "
                    + "Does not modify data or start a job; diagnostics do not prove infeasibility.")
    public PrecheckResponse precheckSession(@PathVariable("id") @Min(1) long id,
                                            @Valid @RequestBody PrecheckRequest request) {
        throw ContractStub.notImplemented();
    }

    @PostMapping(ApiPaths.SESSIONS + "/{id}/solve")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(operationId = "solveSession", summary = "Queue a manual calculation",
            description = "ORGANIZER owner only. Snapshots input at expectedInputVersion; a version mismatch is 409. "
                    + "May run before collection closes; such results are marked incomplete and retain missing "
                    + "respondents. Reuses an identical request for the same session, input version, algorithm and "
                    + "parameters; if the session already has an active calculation, returns that job. Otherwise "
                    + "queues a job with its queue position. Any signed seed is valid. Optional baseVariantId must "
                    + "belong to this session; its assignments provide the baseline for S5 and its locks enforce H9. "
                    + "Poll getSolverJob for progress. Completion creates a validated draft when a solution exists; "
                    + "it never publishes automatically. A time limit without a solution is NO_SOLUTION, "
                    + "not a proof of infeasibility or a technical failure.")
    public SolverJobResponse solveSession(@PathVariable("id") @Min(1) long id,
                                          @Valid @RequestBody SolveRequest request) {
        throw ContractStub.notImplemented();
    }

    @GetMapping(ApiPaths.JOBS + "/{id}")
    @Operation(operationId = "getSolverJob", summary = "Poll calculation status and progress",
            description = "ORGANIZER owner of the job's session only; inaccessible jobs return 404. "
                    + "QUEUED, RUNNING, COMPLETED, FAILED and CANCELLED describe execution, while solveStatus "
                    + "describes the solver result. COMPLETED may have NO_SOLUTION and a null resultId; only FAILED "
                    + "denotes a technical error. A completed feasible or optimal result links to a draft variant. "
                    + "Unavailable progress, queue position, error, result and timestamps are explicit nulls.")
    public SolverJobResponse getSolverJob(@PathVariable("id") @Min(1) long id) {
        throw ContractStub.notImplemented();
    }
}
