package ru.vk.itmo.scheduler.slotwise.domain.solver;

import ru.vk.itmo.scheduler.slotwise.domain.Score;

import java.time.Duration;
import java.util.OptionalLong;

/**
 * Receives the best score so far while a solver runs. Called on the solver thread, so it must return quickly.
 */
@FunctionalInterface
public interface ProgressListener {
    ProgressListener NONE = progress -> {
    };

    void onProgress(Progress progress);

    record Progress(Duration elapsed, Score best, OptionalLong penaltyBound) {
    }
}
