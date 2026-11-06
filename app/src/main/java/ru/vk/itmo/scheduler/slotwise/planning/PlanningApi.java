package ru.vk.itmo.scheduler.slotwise.planning;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import ru.vk.itmo.scheduler.slotwise.account.AccountRole;
import ru.vk.itmo.scheduler.slotwise.solving.SolverAlgorithm;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Request/response contracts for personal availability, collection and session rules. */
public final class PlanningApi {
    private PlanningApi() { }

    public record Interval(@NotNull Instant startsAt, @NotNull Instant endsAt,
                           @NotNull BusyImportance importance) { }

    public record SaveBusyRequest(@NotNull @PositiveOrZero Long inputVersion,
                                  @NotNull @Size(max = 2000) List<@NotNull @Valid Interval> intervals) { }

    public record KnownInterval(long id, Instant startsAt, Instant endsAt, String description) { }

    public enum Warning {
        NO_TIMETABLE, NO_FREE_TIME, STUDENT_CRITICAL_IS_SOFT_FOR_GROUP_EVENTS
    }

    public record BusyResponse(long inputVersion, CollectionStatus collectionStatus,
                               ResponseStatus responseStatus, Instant submittedAt,
                               List<Interval> intervals, List<KnownInterval> knownIntervals,
                               List<Warning> warnings) { }

    public record ParticipantResponse(long accountId, String firstName, String lastName,
                                      AccountRole role, ResponseStatus responseStatus,
                                      Instant submittedAt, boolean noFreeTime) { }

    public record CollectionResponse(long inputVersion, CollectionStatus status,
                                     Instant startsAt, Instant endsAt,
                                     List<ParticipantResponse> participants) { }

    public record ChangeCollectionRequest(@NotNull @PositiveOrZero Long inputVersion,
                                          @NotNull Instant endsAt) { }

    public record SaveRulesRequest(@NotNull @PositiveOrZero Long inputVersion,
                                   @Schema(description = "Complete set of integer weights S1, S2, S3, S4, S5")
                                   @NotNull @Size(min = 5, max = 5) Map<String, @NotNull @PositiveOrZero Integer> weights,
                                   @NotNull @PositiveOrZero Integer roomChangeBufferMinutes,
                                   @NotNull SolverAlgorithm defaultAlgorithm,
                                   @NotNull @Positive Integer timeLimitSeconds,
                                   @NotNull Long seed) { }

    public record RulesResponse(long inputVersion, Map<String, Integer> weights,
                                int roomChangeBufferMinutes, SolverAlgorithm defaultAlgorithm,
                                int timeLimitSeconds, long seed) { }
}
