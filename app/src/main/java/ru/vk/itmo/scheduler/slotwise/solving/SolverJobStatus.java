package ru.vk.itmo.scheduler.slotwise.solving;

public enum SolverJobStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}
