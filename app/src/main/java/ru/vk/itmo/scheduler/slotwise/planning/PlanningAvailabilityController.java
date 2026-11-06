package ru.vk.itmo.scheduler.slotwise.planning;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.vk.itmo.scheduler.slotwise.auth.AccountPrincipal;

import static ru.vk.itmo.scheduler.slotwise.planning.PlanningApi.*;

@RestController
@RequestMapping("/api/sessions/{id}")
@RequiredArgsConstructor
public class PlanningAvailabilityController {
    private final PlanningAvailabilityService service;

    @GetMapping("/busy-intervals/me")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
    @Operation(summary = "Моя личная и учебная занятость", description = "Доступно только участнику сессии. Учебная занятость доступна только для чтения.")
    public BusyResponse busy(@PathVariable long id, @AuthenticationPrincipal AccountPrincipal user) {
        return service.busy(id, user.getAccountId());
    }

    @PutMapping("/busy-intervals/me")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
    @Operation(summary = "Заменить личную занятость", description = "Округление наружу до 5 минут, обрезка по рабочим часам и объединение пересечений. inputVersion защищает от устаревшей записи (409).")
    public BusyResponse saveBusy(@PathVariable long id, @AuthenticationPrincipal AccountPrincipal user,
                                 @Valid @RequestBody SaveBusyRequest request) {
        return service.saveBusy(id, user.getAccountId(), request);
    }

    @PostMapping("/busy-intervals/me/submit")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
    @Operation(summary = "Отправить сохранённый календарь", description = "Пустой календарь допустим. Повторная отправка идемпотентна. Окно сбора должно быть открыто.")
    public BusyResponse submit(@PathVariable long id, @AuthenticationPrincipal AccountPrincipal user) {
        return service.submit(id, user.getAccountId());
    }

    @GetMapping("/collection")
    @PreAuthorize("hasRole('ORGANIZER')")
    @Operation(summary = "Статусы сбора занятости", description = "Доступно только владельцу сессии.")
    public CollectionResponse collection(@PathVariable long id, @AuthenticationPrincipal AccountPrincipal user) {
        return service.collection(id, user.getAccountId());
    }

    @PatchMapping("/collection")
    @PreAuthorize("hasRole('ORGANIZER')")
    @Operation(summary = "Изменить дедлайн сбора", description = "До закрытия сбора; новый дедлайн должен быть в будущем и строго до начала сессии. Ответы сохраняются.")
    public CollectionResponse changeCollection(@PathVariable long id, @AuthenticationPrincipal AccountPrincipal user,
                                               @Valid @RequestBody ChangeCollectionRequest request) {
        return service.changeCollection(id, user.getAccountId(), request);
    }

    @PostMapping("/collection/close")
    @PreAuthorize("hasRole('ORGANIZER')")
    @Operation(summary = "Закрыть сбор", description = "Повторное закрытие идемпотентно. Солвер автоматически не запускается (D-15).")
    public CollectionResponse close(@PathVariable long id, @AuthenticationPrincipal AccountPrincipal user) {
        return service.close(id, user.getAccountId());
    }

    @GetMapping("/rules")
    @PreAuthorize("hasRole('ORGANIZER')")
    @Operation(summary = "Получить правила сессии", description = "Доступно только владельцу сессии.")
    public RulesResponse rules(@PathVariable long id, @AuthenticationPrincipal AccountPrincipal user) {
        return service.rules(id, user.getAccountId());
    }

    @PutMapping("/rules")
    @PreAuthorize("hasRole('ORGANIZER')")
    @Operation(summary = "Заменить правила сессии", description = "Веса S1–S5 неотрицательные, буфер кратен 5 минутам. inputVersion защищает от устаревшей записи (409).")
    public RulesResponse saveRules(@PathVariable long id, @AuthenticationPrincipal AccountPrincipal user,
                                   @Valid @RequestBody SaveRulesRequest request) {
        return service.saveRules(id, user.getAccountId(), request);
    }
}
