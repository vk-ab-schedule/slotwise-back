package ru.vk.itmo.scheduler.slotwise.planning;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vk.itmo.scheduler.slotwise.common.api.ApiIssue;
import ru.vk.itmo.scheduler.slotwise.common.api.IssueSeverity;
import ru.vk.itmo.scheduler.slotwise.common.api.PageResponse;
import ru.vk.itmo.scheduler.slotwise.eventtype.EventGranularity;
import ru.vk.itmo.scheduler.slotwise.eventtype.EventTypeCatalog;
import ru.vk.itmo.scheduler.slotwise.eventtype.ParticipantRole;
import ru.vk.itmo.scheduler.slotwise.planning.EventParticipants.Target;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.CreateEventsRequest;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.CreateEventsResponse;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.EventParticipant;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.EventResponse;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.UpdateEventRequest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static ru.vk.itmo.scheduler.slotwise.planning.SessionPreparationService.conflict;
import static ru.vk.itmo.scheduler.slotwise.planning.SessionPreparationService.invalid;
import static ru.vk.itmo.scheduler.slotwise.planning.SessionPreparationService.requireVersion;

@Service
@RequiredArgsConstructor
public class PlanningEventService {
    private final SessionPreparationStore sessions;
    private final SessionMembership membership;
    private final EventTypeCatalog types;
    private final EventParticipants people;
    private final PlanningEventStore events;

    @Transactional
    public PageResponse<EventResponse> list(long id, long owner, int page, int size) {
        sessions.owned(id, owner, false);
        return events.list(id, page, size);
    }

    @Transactional
    public EventResponse get(long id, long event, long owner) {
        sessions.owned(id, owner, false);
        return events.get(id, event);
    }

    @Transactional
    public CreateEventsResponse create(long id, long owner, CreateEventsRequest request) {
        requireVersion(sessions.owned(id, owner, true), request.expectedVersion());
        var type = types.get(request.eventTypeId());
        if (!type.active()) {
            throw invalid("INACTIVE_EVENT_TYPE", "Выберите активный тип события");
        }
        if (request.disciplineId() != null) {
            membership.requireCatalogIds(Set.of(request.disciplineId()),
                    "select count(*) from slotwise.discipline where active and id in (:ids)",
                    "INVALID_DISCIPLINE", "Выберите активную дисциплину");
        }
        var groups = people.groups(request.studyGroupIds());
        if (groups.size() != request.studyGroupIds().size()) {
            throw invalid("INVALID_STUDY_GROUP", "Выберите активные учебные группы");
        }
        var groupStudents = people.groupStudents(request.studyGroupIds());
        var students = new TreeSet<>(request.studentIds());
        groupStudents.values().forEach(students::addAll);
        var names = people.studentNames(students);
        if (names.size() != students.size()) {
            throw invalid("INVALID_STUDENT", "Выберите активных студентов");
        }
        boolean perGroup = type.granularity() == EventGranularity.PER_GROUP;
        if (perGroup && groups.isEmpty() || !perGroup && students.isEmpty()) {
            throw invalid("MISSING_EVENT_TARGETS", "Для этого типа выберите учебные группы или студентов");
        }

        // For a student selected directly, a unique group (or a unique match for the discipline) is sufficient.
        var studentGroups = perGroup ? Map.<Long, List<Long>>of() : people.studentGroups(students);
        var possibleGroups = new HashSet<>(request.studyGroupIds());
        studentGroups.values().forEach(possibleGroups::addAll);
        boolean needsTeacher = type.participantRoles().contains(ParticipantRole.DISCIPLINE_TEACHER);
        boolean needsSupervisor = type.participantRoles().contains(ParticipantRole.SUPERVISOR);
        var teachers = needsTeacher ? people.teachers(request.disciplineId(), possibleGroups) : Map.<Long, List<Long>>of();
        var supervisors = needsSupervisor ? people.supervisors(students) : Map.<Long, List<Long>>of();
        List<Target> targets = perGroup
                ? groups.stream().map(group -> new Target(group.id(), group.name(),
                        groupStudents.getOrDefault(group.id(), List.of()))).toList()
                : students.stream().map(student -> new Target(
                        studentGroup(studentGroups.getOrDefault(student, List.of()), request.studyGroupIds(), teachers),
                        names.get(student), List.of(student))).toList();

        var created = new ArrayList<Long>();
        var issues = new ArrayList<ApiIssue>();
        for (var target : targets) {
            var participants = new LinkedHashMap<Long, ParticipantRole>();
            var missingSupervisors = new ArrayList<Long>();
            for (long student : target.students()) {
                participants.put(student, ParticipantRole.STUDENT);
                var linked = supervisors.getOrDefault(student, List.of());
                linked.forEach(supervisor -> participants.putIfAbsent(supervisor, ParticipantRole.SUPERVISOR));
                if (needsSupervisor && linked.isEmpty()) {
                    missingSupervisors.add(student);
                }
            }
            var linkedTeachers = target.group() == null ? List.<Long>of() : teachers.getOrDefault(target.group(), List.of());
            linkedTeachers.forEach(teacher -> participants.putIfAbsent(teacher, ParticipantRole.DISCIPLINE_TEACHER));
            boolean noStudent = target.students().isEmpty();
            boolean noTeacher = needsTeacher && linkedTeachers.isEmpty();
            boolean complete = !noStudent && !noTeacher && missingSupervisors.isEmpty();
            long event = events.create(id, type, request.disciplineId(), target.group(), title(type.name(), target.label()), complete);
            membership.add(id, participants.keySet());
            events.writeParticipants(id, event, participants.entrySet().stream()
                    .map(person -> new EventParticipant(person.getKey(), person.getValue())).toList());
            created.add(event);
            if (noStudent) {
                issues.add(issue("MISSING_STUDENT", "В учебной группе нет активных студентов", event, List.of()));
            }
            if (noTeacher) {
                issues.add(issue("MISSING_TEACHER", "Не найден преподаватель дисциплины", event, target.students()));
            }
            if (!missingSupervisors.isEmpty()) {
                issues.add(issue("MISSING_SUPERVISOR", "Не найден научный руководитель", event, missingSupervisors));
            }
        }
        events.addTemplateEdges(id, created);
        var revision = sessions.bump(id);
        return new CreateEventsResponse(events.get(id, created), issues, revision.version(), revision.inputVersion());
    }

    @Transactional
    public EventResponse update(long id, long event, long owner, UpdateEventRequest request) {
        sessions.owned(id, owner, true);
        var existing = events.get(id, event);
        requireEventVersion(existing, request.expectedVersion());
        var accounts = new HashSet<Long>();
        for (var person : request.participants()) {
            if (!accounts.add(person.accountId())) {
                throw invalid("DUPLICATE_EVENT_PARTICIPANT", "Участник события указан дважды");
            }
        }
        var roles = people.activeRoles(accounts);
        for (var person : request.participants()) {
            String requiredRole = person.role() == ParticipantRole.STUDENT ? "STUDENT" : "TEACHER";
            if (!requiredRole.equals(roles.get(person.accountId()))) {
                throw invalid("INVALID_EVENT_PARTICIPANT", "Роль участника не соответствует его роли в вузе");
            }
        }
        membership.requireCatalogIds(request.equipmentIds(), "select count(*) from slotwise.equipment where id in (:ids)",
                "INVALID_EVENT_EQUIPMENT", "Оборудование не найдено");
        var predecessors = new HashSet<Long>();
        for (var dependency : request.dependencies()) {
            if (dependency.predecessorEventId() == event || !predecessors.add(dependency.predecessorEventId())) {
                throw invalid("INVALID_EVENT_DEPENDENCY", "Зависимость не может ссылаться на себя или повторяться");
            }
        }
        events.requirePredecessors(id, predecessors);

        boolean participantsChanged = !Set.copyOf(existing.participants()).equals(Set.copyOf(request.participants()));
        boolean dependenciesChanged = !Set.copyOf(existing.dependencies()).equals(Set.copyOf(request.dependencies()));
        boolean complete = participantsChanged ? completeAfterEdit(existing, request.participants()) : existing.complete();
        if (participantsChanged) {
            membership.add(id, accounts);
            events.writeParticipants(id, event, request.participants());
            membership.reconcile(id);
        }
        events.update(id, event, request, complete, dependenciesChanged);
        sessions.bump(id);
        return events.get(id, event);
    }

    @Transactional
    public void delete(long id, long event, long owner, long expected) {
        sessions.owned(id, owner, true);
        requireEventVersion(events.get(id, event), expected);
        events.delete(id, event);
        membership.reconcile(id);
        sessions.bump(id);
    }

    private boolean completeAfterEdit(EventResponse event, List<EventParticipant> participants) {
        var required = new HashSet<>(events.requiredRoles(event.id()));
        required.add(ParticipantRole.STUDENT);
        participants.forEach(person -> required.remove(person.role()));
        if (required.contains(ParticipantRole.DISCIPLINE_TEACHER) && event.studyGroupId() != null) {
            var teachers = people.teachers(event.disciplineId(), Set.of(event.studyGroupId()))
                    .getOrDefault(event.studyGroupId(), List.of());
            if (participants.stream().anyMatch(person ->
                    person.role() == ParticipantRole.SUPERVISOR && teachers.contains(person.accountId()))) {
                required.remove(ParticipantRole.DISCIPLINE_TEACHER);
            }
        }
        return required.isEmpty();
    }

    private static Long studentGroup(List<Long> groups, Set<Long> requested, Map<Long, List<Long>> teachers) {
        var selected = groups.stream().filter(requested::contains).toList();
        var candidates = selected.isEmpty() ? groups : selected;
        if (candidates.size() == 1) {
            return candidates.getFirst();
        }
        var matches = candidates.stream().filter(teachers::containsKey).toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private static String title(String type, String label) {
        String value = type + " · " + label;
        return value.codePointCount(0, value.length()) <= 255 ? value : value.substring(0, value.offsetByCodePoints(0, 255));
    }

    private static ApiIssue issue(String code, String text, long event, List<Long> accounts) {
        return new ApiIssue(code, text, IssueSeverity.WARNING, List.of(event), accounts, List.of());
    }

    private static void requireEventVersion(EventResponse event, long expected) {
        if (event.version() != expected) {
            throw conflict("STALE_EVENT_VERSION", "Событие изменилось; обновите его и повторите запрос");
        }
    }
}
