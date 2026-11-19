package ru.vk.itmo.scheduler.slotwise.domain.validation;

/**
 * Soft constraints of MODEL.md with the units of their raw penalty.
 */
public enum SoftConstraint {
    /** Teacher gaps between events of the same day, minutes. */
    S1,
    /** Commission load spread: max minus min over the whole pool, minutes. */
    S2,
    /** Overlap with soft busy time times its coefficient, minutes. */
    S3,
    /** Teacher switches between rooms during a day, count. */
    S4,
    /** Events changed against the baseline, count. */
    S5
}
