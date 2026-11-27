package ru.vk.itmo.scheduler.slotwise.university.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.vk.itmo.scheduler.slotwise.account.AccountRole;
import ru.vk.itmo.scheduler.slotwise.common.api.ApiPaths;
import ru.vk.itmo.scheduler.slotwise.common.api.PageResponse;
import ru.vk.itmo.scheduler.slotwise.common.api.PersonSummary;
import ru.vk.itmo.scheduler.slotwise.university.UniversityCatalogService;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityDisciplineResponse;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityEquipmentResponse;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityGroupResponse;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityRoomResponse;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversitySupervisionResponse;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityTeachingResponse;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityTimetableEntryResponse;

import java.time.Instant;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = ApiPaths.UNIVERSITY, produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
@Tag(name = "University catalogs", description = "ADMIN and ORGANIZER; public person summaries omit email and credentials.")
public class UniversityCatalogController {
    private final UniversityCatalogService catalog;

    @GetMapping("/people")
    @Operation(operationId = "listUniversityPeople", summary = "List people")
    public PageResponse<PersonSummary> listUniversityPeople(
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(name = "search", required = false) @Size(max = 255) String search,
            @RequestParam(name = "role", required = false) AccountRole role,
            @RequestParam(name = "groupId", required = false) @Min(1) Long groupId,
            @RequestParam(name = "enabled", required = false) Boolean enabled) {
        return catalog.listPeople(page, size, search, role, groupId, enabled);
    }

    @GetMapping("/groups")
    @Operation(operationId = "listUniversityGroups", summary = "List groups")
    public PageResponse<UniversityGroupResponse> listUniversityGroups(
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(name = "search", required = false) @Size(max = 255) String search,
            @RequestParam(name = "studentId", required = false) @Min(1) Long studentId,
            @RequestParam(name = "active", required = false) Boolean active) {
        return catalog.listGroups(page, size, search, studentId, active);
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
        return catalog.listRooms(page, size, search, equipmentId, minCapacity, online, building, active);
    }

    @GetMapping("/disciplines")
    @Operation(operationId = "listUniversityDisciplines", summary = "List disciplines")
    public PageResponse<UniversityDisciplineResponse> listUniversityDisciplines(
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(name = "search", required = false) @Size(max = 255) String search,
            @RequestParam(name = "active", required = false) Boolean active) {
        return catalog.listDisciplines(page, size, search, active);
    }

    @GetMapping("/equipment")
    @Operation(operationId = "listUniversityEquipment", summary = "List equipment")
    public PageResponse<UniversityEquipmentResponse> listUniversityEquipment(
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(name = "search", required = false) @Size(max = 255) String search) {
        return catalog.listEquipment(page, size, search);
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
        return catalog.listTeachings(page, size, search, teacherId, disciplineId, groupId);
    }

    @GetMapping("/supervisions")
    @Operation(operationId = "listUniversitySupervisions", summary = "List supervisions")
    public PageResponse<UniversitySupervisionResponse> listUniversitySupervisions(
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(name = "search", required = false) @Size(max = 255) String search,
            @RequestParam(name = "studentId", required = false) @Min(1) Long studentId,
            @RequestParam(name = "supervisorId", required = false) @Min(1) Long supervisorId) {
        return catalog.listSupervisions(page, size, search, studentId, supervisorId);
    }

    @GetMapping("/timetable")
    @Operation(operationId = "listUniversityTimetable", summary = "List timetable",
            description = "Returns entries overlapping the requested interval [startsAt, endsAt). "
                    + "Each boundary is optional. When both are present, startsAt must precede endsAt.")
    public PageResponse<UniversityTimetableEntryResponse> listUniversityTimetable(
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(name = "search", required = false) @Size(max = 255) String search,
            @RequestParam(name = "accountId", required = false) @Min(1) Long accountId,
            @RequestParam(name = "roomId", required = false) @Min(1) Long roomId,
            @RequestParam(name = "startsAt", required = false) Instant startsAt,
            @RequestParam(name = "endsAt", required = false) Instant endsAt) {
        return catalog.listTimetable(page, size, search, accountId, roomId, startsAt, endsAt);
    }

    @GetMapping("/groups/{id}")
    @Operation(operationId = "getUniversityGroup", summary = "Get groups record")
    public UniversityGroupResponse getUniversityGroup(
            @PathVariable("id") @Min(1) long id) {
        return catalog.getGroup(id);
    }

    @GetMapping("/rooms/{id}")
    @Operation(operationId = "getUniversityRoom", summary = "Get rooms record")
    public UniversityRoomResponse getUniversityRoom(
            @PathVariable("id") @Min(1) long id) {
        return catalog.getRoom(id);
    }

    @GetMapping("/disciplines/{id}")
    @Operation(operationId = "getUniversityDiscipline", summary = "Get disciplines record")
    public UniversityDisciplineResponse getUniversityDiscipline(
            @PathVariable("id") @Min(1) long id) {
        return catalog.getDiscipline(id);
    }

    @GetMapping("/equipment/{id}")
    @Operation(operationId = "getUniversityEquipment", summary = "Get equipment record")
    public UniversityEquipmentResponse getUniversityEquipment(
            @PathVariable("id") @Min(1) long id) {
        return catalog.getEquipment(id);
    }

    @GetMapping("/timetable/{id}")
    @Operation(operationId = "getUniversityTimetableEntry", summary = "Get timetable record")
    public UniversityTimetableEntryResponse getUniversityTimetableEntry(
            @PathVariable("id") @Min(1) long id) {
        return catalog.getTimetableEntry(id);
    }

    @GetMapping("/people/{id}")
    @Operation(operationId = "getUniversityPerson", summary = "Get university person")
    public PersonSummary getUniversityPerson(@PathVariable("id") @Min(1) long id) {
        return catalog.getPerson(id);
    }

    @GetMapping("/teachings/{id}")
    @Operation(operationId = "getUniversityTeaching", summary = "Get university teaching")
    public UniversityTeachingResponse getUniversityTeaching(@PathVariable("id") @Min(1) long id) {
        return catalog.getTeaching(id);
    }

    @GetMapping("/supervisions/{studentId}/{supervisorId}")
    @Operation(operationId = "getUniversitySupervision", summary = "Get supervision")
    public UniversitySupervisionResponse getUniversitySupervision(
            @PathVariable("studentId") @Min(1) long studentId,
            @PathVariable("supervisorId") @Min(1) long supervisorId) {
        return catalog.getSupervision(studentId, supervisorId);
    }
}
