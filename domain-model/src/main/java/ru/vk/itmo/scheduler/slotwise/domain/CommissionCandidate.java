package ru.vk.itmo.scheduler.slotwise.domain;

/**
 * Teacher from the commission pool of the session.
 */
public record CommissionCandidate(long personId, boolean canChair) {
}
