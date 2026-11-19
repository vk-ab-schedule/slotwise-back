package ru.vk.itmo.scheduler.slotwise.datagen;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Sizes of the experiment datasets (MODEL.md, «Эксперимент для защиты»).
 *
 * <p>Events are exams with a consultation for every group and discipline, plus a pre-defense
 * and a defense for every graduating student: {@code 2·groups·examsPerGroup + 2·defenses}.
 * Days are working days. Exams per group fit the session with two preparation days between them.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum DatasetProfile {
    S(10, 3, 3, 2, 1, 13, false),
    M(30, 6, 5, 6, 2, 63, false),
    L(50, 10, 10, 10, 3, 220, false),
    /** Same size as M, with more classes and personal busy time. */
    TIGHT(30, 6, 5, 6, 2, 63, true);

    private final int teachers;
    private final int rooms;
    private final int days;
    private final int groups;
    private final int examsPerGroup;
    private final int defenses;
    private final boolean tight;

    public int events() {
        return 2 * groups * examsPerGroup + 2 * defenses;
    }
}
