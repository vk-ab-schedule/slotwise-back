package ru.vk.itmo.scheduler.slotwise.university.api;

import ru.vk.itmo.scheduler.slotwise.common.api.ApiPaths;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.vk.itmo.scheduler.slotwise.account.AccountRole;
import ru.vk.itmo.scheduler.slotwise.common.api.ContractStub;
import ru.vk.itmo.scheduler.slotwise.common.api.PageResponse;
import ru.vk.itmo.scheduler.slotwise.common.api.PersonSummary;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityDisciplineResponse;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityEquipmentResponse;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityGroupResponse;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityRoomResponse;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversitySupervisionResponse;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityTeachingResponse;

@RestController
@RequestMapping(value = ApiPaths.UNIVERSITY, produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
@Tag(name = "University catalogs", description = "ADMIN and ORGANIZER; public person summaries omit email and credentials.")
public class UniversityCatalogController {

    @GetMapping("/people")
    @Operation(operationId = "listUniversityPeople", summary = "List people")
    public PageResponse<PersonSummary> listUniversityPeople(
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(name = "search", required = false) @Size(max = 255) String search,
            @RequestParam(name = "role", required = false) AccountRole role,
            @RequestParam(name = "groupId", required = false) @Min(1) Long groupId,
            @RequestParam(name = "enabled", required = false) Boolean enabled) {
        throw ContractStub.notImplemented();
    }

    @GetMapping("/groups")
    @Operation(operationId = "listUniversityGroups", summary = "List groups")
    public PageResponse<UniversityGroupResponse> listUniversityGroups(
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(name = "search", required = false) @Size(max = 255) String search,
            @RequestParam(name = "studentId", required = false) @Min(1) Long studentId,
            @RequestParam(name = "active", required = false) Boolean active) {
        throw ContractStub.notImplemented();
    }

    @GetMapping("/rooms")
    @Operation(operationId = "listUniversityRooms", summary = "List rooms")
    public PageResponse<UniversityRoomResponse> listUniversityRooms(
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(name = "search", required = false) @Size(max = 255) String search,
            @RequestParam(name = "equipmentId", required = false) @Min(1) Long equipmentId,
            @RequestParam(name = "minCapacity", required = false) @Min(1) Integer minCapacity,
            @RequestParam(name = "online", required = false) Boolean online,
            @RequestParam(name = "building", required = false) @Size(max = 255) String building,
            @RequestParam(name = "active", required = false) Boolean active) {
        throw ContractStub.notImplemented();
    }

    @GetMapping("/disciplines")
    @Operation(operationId = "listUniversityDisciplines", summary = "List disciplines")
    public PageResponse<UniversityDisciplineResponse> listUniversityDisciplines(
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(name = "search", required = false) @Size(max = 255) String search,
            @RequestParam(name = "active", required = false) Boolean active) {
        throw ContractStub.notImplemented();
    }

    @GetMapping("/equipment")
    @Operation(operationId = "listUniversityEquipment", summary = "List equipment")
    public PageResponse<UniversityEquipmentResponse> listUniversityEquipment(
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(name = "search", required = false) @Size(max = 255) String search) {
        throw ContractStub.notImplemented();
    }

    @GetMapping("/teachings")
    @Operation(operationId = "listUniversityTeachings", summary = "List teachings")
    public PageResponse<UniversityTeachingResponse> listUniversityTeachings(
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(name = "search", required = false) @Size(max = 255) String search,
            @RequestParam(name = "teacherId", required = false) @Min(1) Long teacherId,
            @RequestParam(name = "disciplineId", required = false) @Min(1) Long disciplineId,
            @RequestParam(name = "groupId", required = false) @Min(1) Long groupId) {
        throw ContractStub.notImplemented();
    }

    @GetMapping("/supervisions")
    @Operation(operationId = "listUniversitySupervisions", summary = "List supervisions")
    public PageResponse<UniversitySupervisionResponse> listUniversitySupervisions(
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(name = "search", required = false) @Size(max = 255) String search,
            @RequestParam(name = "studentId", required = false) @Min(1) Long studentId,
            @RequestParam(name = "supervisorId", required = false) @Min(1) Long supervisorId) {
        throw ContractStub.notImplemented();
    }
}
