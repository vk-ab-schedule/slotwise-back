package ru.vk.itmo.scheduler.slotwise.university.api;

import ru.vk.itmo.scheduler.slotwise.common.api.ApiPaths;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.vk.itmo.scheduler.slotwise.common.api.ContractStub;
import ru.vk.itmo.scheduler.slotwise.common.api.PageResponse;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.CreateUniversityDisciplineRequest;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.CreateUniversityGroupRequest;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.CreateUniversityRoomRequest;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityDisciplineResponse;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityEquipmentRequest;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityEquipmentResponse;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityGroupResponse;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityRoomResponse;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversitySupervisionRequest;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversitySupervisionResponse;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityTeachingRequest;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityTeachingResponse;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityTimetableEntryRequest;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityTimetableEntryResponse;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UpdateUniversityDisciplineRequest;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UpdateUniversityGroupRequest;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UpdateUniversityRoomRequest;

@RestController
@RequestMapping(value = ApiPaths.ADMIN, produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "University administration", description = "ADMIN only; version checks apply only to groups, rooms and disciplines.")
public class AdminUniversityController {

    @GetMapping("/groups")
    @Operation(operationId = "listAdminUniversityGroups", summary = "List groups")
    public PageResponse<UniversityGroupResponse> listAdminUniversityGroups(
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(name = "search", required = false) @Size(max = 255) String search,
            @RequestParam(name = "studentId", required = false) @Min(1) Long studentId,
            @RequestParam(name = "active", required = false) Boolean active) {
        throw ContractStub.notImplemented();
    }

    @GetMapping("/rooms")
    @Operation(operationId = "listAdminUniversityRooms", summary = "List rooms")
    public PageResponse<UniversityRoomResponse> listAdminUniversityRooms(
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
    @Operation(operationId = "listAdminUniversityDisciplines", summary = "List disciplines")
    public PageResponse<UniversityDisciplineResponse> listAdminUniversityDisciplines(
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(name = "search", required = false) @Size(max = 255) String search,
            @RequestParam(name = "active", required = false) Boolean active) {
        throw ContractStub.notImplemented();
    }

    @GetMapping("/equipment")
    @Operation(operationId = "listAdminUniversityEquipment", summary = "List equipment")
    public PageResponse<UniversityEquipmentResponse> listAdminUniversityEquipment(
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(name = "search", required = false) @Size(max = 255) String search) {
        throw ContractStub.notImplemented();
    }

    @GetMapping("/teachings")
    @Operation(operationId = "listAdminUniversityTeachings", summary = "List teachings")
    public PageResponse<UniversityTeachingResponse> listAdminUniversityTeachings(
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(name = "search", required = false) @Size(max = 255) String search,
            @RequestParam(name = "teacherId", required = false) @Min(1) Long teacherId,
            @RequestParam(name = "disciplineId", required = false) @Min(1) Long disciplineId,
            @RequestParam(name = "groupId", required = false) @Min(1) Long groupId) {
        throw ContractStub.notImplemented();
    }

    @GetMapping("/supervisions")
    @Operation(operationId = "listAdminUniversitySupervisions", summary = "List supervisions")
    public PageResponse<UniversitySupervisionResponse> listAdminUniversitySupervisions(
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(name = "search", required = false) @Size(max = 255) String search,
            @RequestParam(name = "studentId", required = false) @Min(1) Long studentId,
            @RequestParam(name = "supervisorId", required = false) @Min(1) Long supervisorId) {
        throw ContractStub.notImplemented();
    }

    @GetMapping("/timetable")
    @Operation(operationId = "listAdminUniversityTimetable", summary = "List timetable")
    public PageResponse<UniversityTimetableEntryResponse> listAdminUniversityTimetable(
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(name = "search", required = false) @Size(max = 255) String search,
            @RequestParam(name = "accountId", required = false) @Min(1) Long accountId,
            @RequestParam(name = "roomId", required = false) @Min(1) Long roomId,
            @RequestParam(name = "startsAt", required = false) Instant startsAt,
            @RequestParam(name = "endsAt", required = false) Instant endsAt) {
        throw ContractStub.notImplemented();
    }

    @GetMapping("/groups/{id}")
    @Operation(operationId = "getAdminUniversityGroup", summary = "Get groups record")
    public UniversityGroupResponse getAdminUniversityGroup(
            @PathVariable("id") @Min(1) long id) {
        throw ContractStub.notImplemented();
    }

    @PostMapping(value = "/groups", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "createUniversityGroup", summary = "Create groups record")
    @ResponseStatus(HttpStatus.CREATED)
    public UniversityGroupResponse createUniversityGroup(
            @Valid @RequestBody CreateUniversityGroupRequest request) {
        throw ContractStub.notImplemented();
    }

    @PutMapping(value = "/groups/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "updateUniversityGroup", summary = "Update groups record")
    public UniversityGroupResponse updateUniversityGroup(
            @PathVariable("id") @Min(1) long id,
            @Valid @RequestBody UpdateUniversityGroupRequest request) {
        throw ContractStub.notImplemented();
    }

    @DeleteMapping("/groups/{id}")
    @Operation(operationId = "deleteUniversityGroup", summary = "Delete groups record",
            description = "Reject records used by events; offer deactivation instead (UX-23).")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUniversityGroup(
            @PathVariable("id") @Min(1) long id,
            @RequestParam("expectedVersion") @PositiveOrZero long expectedVersion) {
        throw ContractStub.notImplemented();
    }

    @GetMapping("/rooms/{id}")
    @Operation(operationId = "getAdminUniversityRoom", summary = "Get rooms record")
    public UniversityRoomResponse getAdminUniversityRoom(
            @PathVariable("id") @Min(1) long id) {
        throw ContractStub.notImplemented();
    }

    @PostMapping(value = "/rooms", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "createUniversityRoom", summary = "Create rooms record")
    @ResponseStatus(HttpStatus.CREATED)
    public UniversityRoomResponse createUniversityRoom(
            @Valid @RequestBody CreateUniversityRoomRequest request) {
        throw ContractStub.notImplemented();
    }

    @PutMapping(value = "/rooms/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "updateUniversityRoom", summary = "Update rooms record")
    public UniversityRoomResponse updateUniversityRoom(
            @PathVariable("id") @Min(1) long id,
            @Valid @RequestBody UpdateUniversityRoomRequest request) {
        throw ContractStub.notImplemented();
    }

    @DeleteMapping("/rooms/{id}")
    @Operation(operationId = "deleteUniversityRoom", summary = "Delete rooms record",
            description = "Reject records used by events; offer deactivation instead (UX-23).")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUniversityRoom(
            @PathVariable("id") @Min(1) long id,
            @RequestParam("expectedVersion") @PositiveOrZero long expectedVersion) {
        throw ContractStub.notImplemented();
    }

    @GetMapping("/disciplines/{id}")
    @Operation(operationId = "getAdminUniversityDiscipline", summary = "Get disciplines record")
    public UniversityDisciplineResponse getAdminUniversityDiscipline(
            @PathVariable("id") @Min(1) long id) {
        throw ContractStub.notImplemented();
    }

    @PostMapping(value = "/disciplines", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "createUniversityDiscipline", summary = "Create disciplines record")
    @ResponseStatus(HttpStatus.CREATED)
    public UniversityDisciplineResponse createUniversityDiscipline(
            @Valid @RequestBody CreateUniversityDisciplineRequest request) {
        throw ContractStub.notImplemented();
    }

    @PutMapping(value = "/disciplines/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "updateUniversityDiscipline", summary = "Update disciplines record")
    public UniversityDisciplineResponse updateUniversityDiscipline(
            @PathVariable("id") @Min(1) long id,
            @Valid @RequestBody UpdateUniversityDisciplineRequest request) {
        throw ContractStub.notImplemented();
    }

    @DeleteMapping("/disciplines/{id}")
    @Operation(operationId = "deleteUniversityDiscipline", summary = "Delete disciplines record",
            description = "Reject records used by events; offer deactivation instead (UX-23).")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUniversityDiscipline(
            @PathVariable("id") @Min(1) long id,
            @RequestParam("expectedVersion") @PositiveOrZero long expectedVersion) {
        throw ContractStub.notImplemented();
    }

    @GetMapping("/equipment/{id}")
    @Operation(operationId = "getAdminUniversityEquipment", summary = "Get equipment record")
    public UniversityEquipmentResponse getAdminUniversityEquipment(
            @PathVariable("id") @Min(1) long id) {
        throw ContractStub.notImplemented();
    }

    @PostMapping(value = "/equipment", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "createUniversityEquipment", summary = "Create equipment record")
    @ResponseStatus(HttpStatus.CREATED)
    public UniversityEquipmentResponse createUniversityEquipment(
            @Valid @RequestBody UniversityEquipmentRequest request) {
        throw ContractStub.notImplemented();
    }

    @PutMapping(value = "/equipment/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "updateUniversityEquipment", summary = "Update equipment record")
    public UniversityEquipmentResponse updateUniversityEquipment(
            @PathVariable("id") @Min(1) long id,
            @Valid @RequestBody UniversityEquipmentRequest request) {
        throw ContractStub.notImplemented();
    }

    @DeleteMapping("/equipment/{id}")
    @Operation(operationId = "deleteUniversityEquipment", summary = "Delete equipment record",
            description = "Reject records used by events; offer deactivation instead (UX-23).")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUniversityEquipment(
            @PathVariable("id") @Min(1) long id) {
        throw ContractStub.notImplemented();
    }

    @GetMapping("/timetable/{id}")
    @Operation(operationId = "getAdminUniversityTimetableEntry", summary = "Get timetable record")
    public UniversityTimetableEntryResponse getAdminUniversityTimetableEntry(
            @PathVariable("id") @Min(1) long id) {
        throw ContractStub.notImplemented();
    }

    @PostMapping(value = "/timetable", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "createUniversityTimetableEntry", summary = "Create timetable record")
    @ResponseStatus(HttpStatus.CREATED)
    public UniversityTimetableEntryResponse createUniversityTimetableEntry(
            @Valid @RequestBody UniversityTimetableEntryRequest request) {
        throw ContractStub.notImplemented();
    }

    @PutMapping(value = "/timetable/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "updateUniversityTimetableEntry", summary = "Update timetable record")
    public UniversityTimetableEntryResponse updateUniversityTimetableEntry(
            @PathVariable("id") @Min(1) long id,
            @Valid @RequestBody UniversityTimetableEntryRequest request) {
        throw ContractStub.notImplemented();
    }

    @DeleteMapping("/timetable/{id}")
    @Operation(operationId = "deleteUniversityTimetableEntry", summary = "Delete timetable record",
            description = "Reject records used by events; offer deactivation instead (UX-23).")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUniversityTimetableEntry(
            @PathVariable("id") @Min(1) long id) {
        throw ContractStub.notImplemented();
    }

    @PostMapping(value = "/teachings", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "createUniversityTeaching", summary = "Create teaching link")
    @ResponseStatus(HttpStatus.CREATED)
    public UniversityTeachingResponse createUniversityTeaching(
            @Valid @RequestBody UniversityTeachingRequest request) {
        throw ContractStub.notImplemented();
    }

    @DeleteMapping("/teachings/{id}")
    @Operation(operationId = "deleteUniversityTeaching", summary = "Delete teaching link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUniversityTeaching(
            @PathVariable("id") @Min(1) long id) {
        throw ContractStub.notImplemented();
    }

    @PostMapping(value = "/supervisions", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "createUniversitySupervision", summary = "Create supervision link")
    @ResponseStatus(HttpStatus.CREATED)
    public UniversitySupervisionResponse createUniversitySupervision(
            @Valid @RequestBody UniversitySupervisionRequest request) {
        throw ContractStub.notImplemented();
    }

    @DeleteMapping("/supervisions/{studentId}/{supervisorId}")
    @Operation(operationId = "deleteUniversitySupervision", summary = "Delete supervision link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUniversitySupervision(
            @PathVariable("studentId") @Min(1) long studentId,
            @PathVariable("supervisorId") @Min(1) long supervisorId) {
        throw ContractStub.notImplemented();
    }
}
