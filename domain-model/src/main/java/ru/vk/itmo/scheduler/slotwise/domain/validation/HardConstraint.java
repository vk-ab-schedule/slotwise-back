package ru.vk.itmo.scheduler.slotwise.domain.validation;

/**
 * Hard constraints of MODEL.md.
 */
public enum HardConstraint {
    /** An event is placed at most once and exists in the problem. */
    H1,
    /** A person is not in two events at once and has a buffer between onsite events in different rooms. */
    H2,
    /** A room holds one event at a time and is not used during its classes. */
    H3,
    /** No event during classes or hard personal busy time of its people. */
    H4,
    /** The venue fits the format, the capacity and the equipment. */
    H5,
    /** The commission has the required size, comes from the pool and has a chair if needed. */
    H6,
    /** Dependent events keep their order and day gap. */
    H7,
    /** The event lies within one working period. */
    H8,
    /** Pinned assignments are unchanged. */
    H9,
    /** A student has enough days between events with preparation. */
    H10
}
