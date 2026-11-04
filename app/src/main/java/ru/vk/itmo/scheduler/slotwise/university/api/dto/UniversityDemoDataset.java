package ru.vk.itmo.scheduler.slotwise.university.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Профиль синтетического набора для демонстрации и проверки планировщика. "
        + "Администратор выбирает его в поле dataset запроса POST /api/admin/demo-data. "
        + "Наборы по MODEL.md: S — малый (30 событий); M — средний (150 событий); "
        + "L — большой (500 событий); TIGHT — 150 событий с урезанной доступностью участников и ресурсов. "
        + "Для воспроизводимости набора в запросе также передаётся seed.")
public enum UniversityDemoDataset {
    S,
    M,
    L,
    TIGHT
}
