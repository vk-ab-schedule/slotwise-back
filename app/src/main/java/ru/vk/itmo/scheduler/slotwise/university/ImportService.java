package ru.vk.itmo.scheduler.slotwise.university;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vk.itmo.scheduler.slotwise.account.AccountRole;
import ru.vk.itmo.scheduler.slotwise.common.api.ApiIssue;
import ru.vk.itmo.scheduler.slotwise.common.api.IssueSeverity;
import ru.vk.itmo.scheduler.slotwise.datagen.DatasetGenerator;
import ru.vk.itmo.scheduler.slotwise.datagen.DatasetProfile;
import ru.vk.itmo.scheduler.slotwise.university.ImportStore.Account;
import ru.vk.itmo.scheduler.slotwise.university.ImportStore.Discipline;
import ru.vk.itmo.scheduler.slotwise.university.ImportStore.Group;
import ru.vk.itmo.scheduler.slotwise.university.ImportStore.Room;
import ru.vk.itmo.scheduler.slotwise.university.ImportStore.Row;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityDemoDataRequest;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityDemoDataResponse;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityImportAccount;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityImportCounts;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityImportDiagnostic;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityImportDiscipline;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityImportGroup;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityImportRequest;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityImportResponse;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityImportRoom;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityImportSections;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityImportSupervision;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityImportTeaching;
import ru.vk.itmo.scheduler.slotwise.university.api.dto.UniversityImportTimetableEntry;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ImportService {
    private static final Pattern BCRYPT_HASH =
            Pattern.compile("\\{bcrypt}\\$2[aby]\\$(0[4-9]|[12][0-9]|3[01])\\$[./A-Za-z0-9]{53}");
    private static final Set<String> TIME_ZONES = Set.copyOf(ZoneId.getAvailableZoneIds());
    private static final Instant EARLIEST_TIMETABLE_TIME = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant LATEST_TIMETABLE_TIME = Instant.parse("+10000-01-01T00:00:00Z");

    private final ImportStore store;

    public ImportService(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc) {
        store = new ImportStore(jdbc, namedJdbc);
    }

    @Transactional
    public UniversityImportResponse importData(UniversityImportRequest request, boolean dryRun, boolean allowPartial) {
        return execute(request, dryRun, allowPartial);
    }

    @Transactional
    public UniversityDemoDataResponse importDemo(UniversityDemoDataRequest request) {
        var data = DatasetGenerator.university(DatasetProfile.valueOf(request.dataset().name()), request.seed());
        var payload = new UniversityImportRequest(
                data.accounts().stream().map(a -> new UniversityImportAccount(a.externalId(), a.email(), a.lastName(),
                        a.firstName(), a.patronymic(), AccountRole.valueOf(a.role().name()), a.timeZone(), a.passwordHash())).toList(),
                data.groups().stream().map(g -> new UniversityImportGroup(g.externalId(), g.name(), g.students())).toList(),
                data.rooms().stream().map(r -> new UniversityImportRoom(r.externalId(), r.name(), r.building(), r.online(),
                        r.capacity(), r.equipment())).toList(),
                data.disciplines().stream().map(d -> new UniversityImportDiscipline(d.externalId(), d.name())).toList(),
                data.teaching().stream().map(t -> new UniversityImportTeaching(t.teacher(), t.discipline(), t.group())).toList(),
                data.supervision().stream().map(s -> new UniversityImportSupervision(s.student(), s.supervisor())).toList(),
                data.timetable().stream().map(t -> new UniversityImportTimetableEntry(t.account(), t.room(), t.start(), t.end())).toList());
        return new UniversityDemoDataResponse(request.dataset(), request.seed(), execute(payload, false, false));
    }

    private UniversityImportResponse execute(UniversityImportRequest request, boolean dryRun, boolean allowPartial) {
        // Serialize verification and application against concurrent writes to the university data.
        if (!dryRun) {
            store.lockImportTables();
        }
        var plan = new Plan();
        var sections = new UniversityImportSections(
                plan.section("accounts", request.accounts(), plan::account),
                plan.section("groups", request.groups(), plan::group),
                plan.section("rooms", request.rooms(), plan::room),
                plan.section("disciplines", request.disciplines(), plan::discipline),
                plan.section("teaching", request.teaching(), plan::teaching),
                plan.section("supervision", request.supervision(), plan::supervision),
                plan.section("timetable", request.timetable(), plan::timetable));
        var summary = sum(sections.accounts(), sections.groups(), sections.rooms(), sections.disciplines(),
                sections.teaching(), sections.supervision(), sections.timetable());
        boolean applied = !dryRun && !plan.changes.isEmpty() && (allowPartial || summary.rejected() == 0);
        if (applied) {
            plan.changes.forEach(Runnable::run);
            store.invalidateSessions(plan.affectedAccounts, plan.affectedGroups, plan.affectedRooms, plan.affectedDisciplines);
        }
        return new UniversityImportResponse(dryRun, allowPartial, applied, summary, sections,
                List.copyOf(plan.diagnostics));
    }

    private static UniversityImportCounts sum(UniversityImportCounts... sections) {
        long total = 0, created = 0, updated = 0, unchanged = 0, rejected = 0;
        for (var section : sections) {
            total += section.total();
            created += section.created();
            updated += section.updated();
            unchanged += section.unchanged();
            rejected += section.rejected();
        }
        return new UniversityImportCounts(total, created, updated, unchanged, rejected);
    }

    private final class Plan {
        private final List<Runnable> changes = new ArrayList<>();
        private final List<UniversityImportDiagnostic> diagnostics = new ArrayList<>();
        private final References<Account> accounts = new References<>(store::account);
        private final References<Group> groups = new References<>(store::group);
        private final References<Room> rooms = new References<>(store::room);
        private final References<Discipline> disciplines = new References<>(store::discipline);
        private final Set<String> emails = new HashSet<>();
        private final Set<String> groupNames = new HashSet<>();
        private final Set<UniversityImportTeaching> teachings = new HashSet<>();
        private final Set<UniversityImportSupervision> supervisions = new HashSet<>();
        private final Set<TimetableKey> intervals = new HashSet<>();
        private final Set<Long> affectedAccounts = new HashSet<>();
        private final Set<Long> affectedGroups = new HashSet<>();
        private final Set<Long> affectedRooms = new HashSet<>();
        private final Set<Long> affectedDisciplines = new HashSet<>();

        private <T> UniversityImportCounts section(String section, List<T> rows, Function<T, Outcome> inspect) {
            return section(section, rows, (row, _) -> inspect.apply(row));
        }

        private <T> UniversityImportCounts section(String section, List<T> rows, BiFunction<T, String, Outcome> inspect) {
            var counts = new int[Outcome.values().length];
            for (int index = 0; index < rows.size(); index++) {
                String path = section + "[" + index + "]";
                try {
                    counts[inspect.apply(rows.get(index), path).ordinal()]++;
                } catch (Rejected rejected) {
                    counts[Outcome.REJECTED.ordinal()]++;
                    diagnostics.add(new UniversityImportDiagnostic(path + rejected.field,
                            new ApiIssue(rejected.code, rejected.getMessage(), IssueSeverity.ERROR,
                                    List.of(), List.of(), List.of())));
                }
            }
            return new UniversityImportCounts(rows.size(), counts[Outcome.CREATED.ordinal()],
                    counts[Outcome.UPDATED.ordinal()], counts[Outcome.UNCHANGED.ordinal()],
                    counts[Outcome.REJECTED.ordinal()]);
        }

        private Outcome account(UniversityImportAccount input, String path) {
            var previous = accounts.claim(input.externalId());
            String email = input.email().strip().toLowerCase(Locale.ROOT);
            require(!emails.contains(email) && !store.emailUsedByOther(email, idOf(previous)),
                    ".email", "EMAIL_CONFLICT", "Email is already used by another account");
            require(TIME_ZONES.contains(input.timeZone()), ".timeZone",
                    "INVALID_TIME_ZONE", "Time zone must be an IANA zone");
            String hash = input.passwordHash();
            require(hash == null || BCRYPT_HASH.matcher(hash).matches(), ".passwordHash",
                    "UNSUPPORTED_PASSWORD_HASH", "Password hash must be a supported Spring bcrypt hash");
            if (previous != null) {
                require(previous.value.role() != AccountRole.ADMIN || input.role() == AccountRole.ADMIN, ".role",
                        "ADMIN_ROLE_CHANGE", "Use account management to change an administrator's role");
                if (hash == null) {
                    hash = previous.value.passwordHash();
                }
            }
            var next = new Account(email, input.firstName(), input.lastName(), input.patronymic(), input.role(),
                    input.timeZone(), hash);
            boolean roleChanged = previous != null && previous.value.role() != next.role();
            if (roleChanged && store.isPlanningParticipant(previous.id)) {
                diagnostics.add(new UniversityImportDiagnostic(path + ".role",
                        new ApiIssue("PLANNING_PARTICIPANT_ROLE_CHANGED",
                                "The account participates in planning sessions. Its account role will change from "
                                        + previous.value.role() + " to " + next.role() + "; event roles stay unchanged",
                                IssueSeverity.WARNING, List.of(), List.of(previous.id), List.of())));
            }
            emails.add(email);
            return stage(accounts, input.externalId(), previous, next, row -> {
                store.saveAccount(input.externalId(), previous, row);
                if (roleChanged) {
                    affectedAccounts.add(row.id);
                }
            });
        }

        private Outcome group(UniversityImportGroup input) {
            var previous = groups.claim(input.externalId());
            String name = displayName(input.name(), input.externalId());
            require(!groupNames.contains(name) && !store.groupNameUsedByOther(name, idOf(previous)),
                    ".name", "GROUP_NAME_CONFLICT", "Another group has this name");
            var students = new LinkedHashSet<Row<Account>>();
            for (int i = 0; i < input.students().size(); i++) {
                String field = ".students[" + i + "]";
                var student = accounts.resolve(input.students().get(i), field);
                requireRole(student, AccountRole.STUDENT, field);
                require(students.add(student), field, "DUPLICATE_REFERENCE", "Student is listed twice");
            }
            groupNames.add(name);
            var next = new Group(name, students.stream().map(student -> student.id).collect(Collectors.toSet()));
            return stage(groups, input.externalId(), previous, next, row -> {
                store.saveGroup(input.externalId(), previous, row, students.stream().map(student -> student.id).toList());
                affectedGroups.add(row.id);
            });
        }

        private Outcome room(UniversityImportRoom input) {
            var previous = rooms.claim(input.externalId());
            require(!input.online() || previous == null || !store.roomHasTimetable(previous.id),
                    ".online", "ROOM_HAS_TIMETABLE", "A room with university classes cannot become an online platform");
            var equipment = new LinkedHashSet<>(input.equipment());
            var next = new Room(displayName(input.name(), input.externalId()), input.building(), input.online(),
                    input.capacity(), equipment);
            return stage(rooms, input.externalId(), previous, next, row -> {
                store.saveRoom(input.externalId(), previous, row);
                affectedRooms.add(row.id);
            });
        }

        private Outcome discipline(UniversityImportDiscipline input) {
            var previous = disciplines.claim(input.externalId());
            return stage(disciplines, input.externalId(), previous, new Discipline(input.name()), row -> {
                store.saveDiscipline(input.externalId(), previous, row);
                affectedDisciplines.add(row.id);
            });
        }

        private Outcome teaching(UniversityImportTeaching input) {
            var teacher = accounts.resolve(input.teacher(), ".teacher");
            requireRole(teacher, AccountRole.TEACHER, ".teacher");
            var discipline = disciplines.resolve(input.discipline(), ".discipline");
            var group = groups.resolve(input.group(), ".group");
            if (!teachings.add(input) || store.hasTeaching(teacher, discipline, group)) {
                return Outcome.UNCHANGED;
            }
            changes.add(() -> {
                store.addTeaching(teacher.id, discipline.id, group.id);
                affectedGroups.add(group.id);
            });
            return Outcome.CREATED;
        }

        private Outcome supervision(UniversityImportSupervision input) {
            var student = accounts.resolve(input.student(), ".student");
            var supervisor = accounts.resolve(input.supervisor(), ".supervisor");
            requireRole(student, AccountRole.STUDENT, ".student");
            requireRole(supervisor, AccountRole.TEACHER, ".supervisor");
            if (!supervisions.add(input) || store.hasSupervision(student, supervisor)) {
                return Outcome.UNCHANGED;
            }
            changes.add(() -> {
                store.addSupervision(student.id, supervisor.id);
                affectedAccounts.add(student.id);
            });
            return Outcome.CREATED;
        }

        private Outcome timetable(UniversityImportTimetableEntry input) {
            require((input.account() == null) != (input.room() == null), "",
                    "INVALID_TIMETABLE_TARGET", "Exactly one of account and room is required");
            var account = input.account() == null ? null : accounts.resolve(input.account(), ".account");
            var room = input.room() == null ? null : rooms.resolve(input.room(), ".room");
            require(room == null || !room.value.online(), ".room", "ONLINE_ROOM_TIMETABLE",
                    "An online platform cannot have university classes");
            // PostgreSQL timestamps have microsecond precision; use the stored precision for identity and validation.
            Instant start = input.start().truncatedTo(ChronoUnit.MICROS);
            Instant end = input.end().truncatedTo(ChronoUnit.MICROS);
            require(end.isAfter(start) && !start.isBefore(EARLIEST_TIMETABLE_TIME) && end.isBefore(LATEST_TIMETABLE_TIME),
                    ".end", "INVALID_TIMETABLE_INTERVAL", "Use a supported date range with end after start");
            var key = new TimetableKey(input.account(), input.room(), start, end);
            if (!intervals.add(key) || store.hasTimetable(account, room, start, end)) {
                return Outcome.UNCHANGED;
            }
            changes.add(() -> {
                store.addTimetable(idOf(account), idOf(room), start, end);
                if (account != null) {
                    affectedAccounts.add(account.id);
                } else {
                    affectedRooms.add(room.id);
                }
            });
            return Outcome.CREATED;
        }

        private <T> Outcome stage(References<T> references, String externalId, Row<T> previous, T next,
                                  Consumer<Row<T>> write) {
            var row = new Row<>(idOf(previous), next);
            references.next.put(externalId, row);
            if (previous != null && previous.value.equals(next)) {
                return Outcome.UNCHANGED;
            }
            changes.add(() -> write.accept(row));
            return previous == null ? Outcome.CREATED : Outcome.UPDATED;
        }
    }

    private static String displayName(String value, String externalId) {
        return value == null || value.isBlank() ? externalId : value;
    }

    private static Long idOf(Row<?> row) {
        return row == null ? null : row.id;
    }

    private static void requireRole(Row<Account> row, AccountRole role, String field) {
        require(row.value.role() == role, field, "UNEXPECTED_ACCOUNT_ROLE", "Expected account role " + role);
    }

    private static void require(boolean condition, String field, String code, String message) {
        if (!condition) {
            throw new Rejected(field, code, message);
        }
    }

    private record TimetableKey(String account, String room, Instant start, Instant end) {
    }

    private enum Outcome {
        CREATED,
        UPDATED,
        UNCHANGED,
        REJECTED
    }

    private static final class References<T> {
        private final Function<String, Row<T>> lookup;
        private final Set<String> seen = new HashSet<>();
        private final Map<String, Row<T>> stored = new HashMap<>();
        private final Map<String, Row<T>> next = new HashMap<>();

        private References(Function<String, Row<T>> lookup) {
            this.lookup = lookup;
        }

        private Row<T> claim(String externalId) {
            require(seen.add(externalId), ".externalId", "DUPLICATE_EXTERNAL_ID", "External ID is listed twice");
            return stored.computeIfAbsent(externalId, lookup);
        }

        private Row<T> resolve(String externalId, String field) {
            Row<T> row = seen.contains(externalId) ? next.get(externalId) : stored.computeIfAbsent(externalId, lookup);
            require(row != null, field, "MISSING_REFERENCE", "Referenced record is missing or rejected");
            return row;
        }
    }

    private static final class Rejected extends RuntimeException {
        private final String field;
        private final String code;

        private Rejected(String field, String code, String message) {
            super(message, null, false, false);
            this.field = field;
            this.code = code;
        }
    }
}
