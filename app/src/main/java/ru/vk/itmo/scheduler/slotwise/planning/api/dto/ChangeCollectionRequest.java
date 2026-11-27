package ru.vk.itmo.scheduler.slotwise.planning.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;

public record ChangeCollectionRequest(@NotNull @PositiveOrZero Long expectedVersion,
                                      @NotNull Instant endsAt) { }
