package ru.vk.itmo.scheduler.slotwise.university.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.vk.itmo.scheduler.slotwise.common.api.ApiPaths;
import ru.vk.itmo.scheduler.slotwise.common.api.ContractStub;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityDemoDataRequest;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityDemoDataResponse;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityImportRequest;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityImportResponse;

@RestController
@RequestMapping(value = ApiPaths.ADMIN, produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "University import", description = "ADMIN only; JSON import and reproducible demo data (UX-04, D-02, D-03).")
public class UniversityImportController {

    @PostMapping(value = "/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "importUniversityData", summary = "Preview or apply university JSON",
            description = "References use external IDs. Dry run previews counts and diagnostics without applying changes. Apply uses external IDs for idempotent upsert; valid records may be applied despite errors only with explicit allowPartial=true. Diagnostics never echo credentials.")
    public UniversityImportResponse importUniversityData(
            @RequestParam(name = "dryRun", defaultValue = "true") boolean dryRun,
            @RequestParam(name = "allowPartial", defaultValue = "false") boolean allowPartial,
            @Valid @RequestBody UniversityImportRequest request) {
        throw ContractStub.notImplemented();
    }

    @PostMapping(value = "/demo-data", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "generateUniversityDemoData", summary = "Сгенерировать и загрузить демо-данные вуза",
            description = "Доступно только администратору. Используется для подготовки демонстрации системы на синтетических данных. "
                    + "В dataset выбирается профиль: S — малый, M — средний, L — большой, TIGHT — с урезанной доступностью. "
                    + "Обязательный seed позволяет воспроизвести набор при одинаковых настройках генератора. "
                    + "Сгенерированные данные используют тот же контракт JSON-импорта данных вуза. "
                    + "Сейчас операция является заглушкой и возвращает 501 NOT_IMPLEMENTED; данные не создаются.")
    public UniversityDemoDataResponse generateUniversityDemoData(
            @Valid @RequestBody UniversityDemoDataRequest request) {
        throw ContractStub.notImplemented();
    }
}
