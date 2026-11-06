package ru.vk.itmo.scheduler.slotwise.university.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Запрос администратора на генерацию и загрузку синтетических данных вуза для демонстрации системы.")
public record UniversityDemoDataRequest(
        @NotNull
        @Schema(description = "Профиль демо-набора: S — малый, M — средний, L — большой, TIGHT — с урезанной доступностью.", example = "S")
        UniversityDemoDataset dataset,
        @NotNull
        @Schema(description = "Начальное значение генератора случайных чисел. Одинаковые профиль, seed и настройки генератора воспроизводят один набор данных.", example = "42")
        Long seed) {
}
