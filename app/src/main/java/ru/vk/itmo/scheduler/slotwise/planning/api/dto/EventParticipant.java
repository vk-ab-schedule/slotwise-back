package ru.vk.itmo.scheduler.slotwise.planning.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import ru.vk.itmo.scheduler.slotwise.eventtype.ParticipantRole;

public record EventParticipant(@NotNull @Min(1) Long accountId, @NotNull ParticipantRole role) {
}
