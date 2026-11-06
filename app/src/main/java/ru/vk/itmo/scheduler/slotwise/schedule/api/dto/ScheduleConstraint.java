package ru.vk.itmo.scheduler.slotwise.schedule.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "S1: same-day teacher gaps in minutes; "
        + "S2: maximum minus minimum commission workload in minutes across the entire commission pool, including zero workloads; "
        + "S3: personal busy overlap minutes for each affected participant, multiplied by importance coefficient "
        + "MEDIUM=3, LOW=1, or CRITICAL=10 for a student in a GROUP event; "
        + "S4: same-day teacher room transitions between in-person events (count); "
        + "S5: events changed in time, room or commission from the baseline (count). "
        + "These five soft constraints form P; unassigned events U are compared independently before P."
        + "\n\nПодробнее: [Мягкие ограничения S1–S5](https://github.com/vk-ab-schedule/slotwise-doc/blob/main/MODEL.md#мягкие-ограничения).")
public enum ScheduleConstraint {
    S1,
    S2,
    S3,
    S4,
    S5
}
