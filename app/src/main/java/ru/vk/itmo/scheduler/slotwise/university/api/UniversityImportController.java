package ru.vk.itmo.scheduler.slotwise.university.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.vk.itmo.scheduler.slotwise.common.api.ApiPaths;
import ru.vk.itmo.scheduler.slotwise.university.ImportService;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityDemoDataRequest;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityDemoDataResponse;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityImportRequest;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityImportResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = ApiPaths.ADMIN, produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "University import", description = "ADMIN only; JSON import and reproducible demo data (UX-04, D-02, D-03).")
public class UniversityImportController {
    private final ImportService service;

    @PostMapping(value = "/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "importUniversityData", summary = "Preview or apply university JSON",
            description = "Dry run previews changes without writing to the database. Accounts, groups, rooms and "
                    + "disciplines match by external ID; links and timetable rows match by their fields. "
                    + "Apply can persist valid records despite domain errors only with allowPartial=true. "
                    + "A role change for a planning participant is reported as a warning. "
                    + "Invalid JSON or DTO fields return 400. Diagnostics never echo credentials.")
    public UniversityImportResponse importUniversityData(
            @RequestParam(name = "dryRun", defaultValue = "true") boolean dryRun,
            @RequestParam(name = "allowPartial", defaultValue = "false") boolean allowPartial,
            @Valid @RequestBody UniversityImportRequest request) {
        return service.importData(request, dryRun, allowPartial);
    }

    @PostMapping(value = "/demo-data", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "generateUniversityDemoData", summary = "Сгенерировать и загрузить демо-данные вуза",
            description = "Доступно только администратору. Используется для подготовки демонстрации системы на синтетических данных. "
                    + "В dataset выбирается профиль: S — малый, M — средний, L — большой, TIGHT — с урезанной доступностью. "
                    + "Обязательный seed позволяет воспроизвести набор при одинаковых настройках генератора. "
                    + "Разные пары профиля и seed создают отдельные наборы; повторный импорт той же пары не создаёт дублей. "
                    + "Данные записываются через тот же сервис, что и JSON-импорт.")
    public UniversityDemoDataResponse generateUniversityDemoData(
            @Valid @RequestBody UniversityDemoDataRequest request) {
        return service.importDemo(request);
    }
}
