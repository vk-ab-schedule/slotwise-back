package ru.vk.itmo.scheduler.slotwise.datagen;

import ru.vk.itmo.scheduler.slotwise.domain.CommissionCandidate;
import ru.vk.itmo.scheduler.slotwise.domain.Dependency;
import ru.vk.itmo.scheduler.slotwise.domain.Event;
import ru.vk.itmo.scheduler.slotwise.domain.Person;
import ru.vk.itmo.scheduler.slotwise.domain.Problem;
import ru.vk.itmo.scheduler.slotwise.domain.Room;
import ru.vk.itmo.scheduler.slotwise.domain.SlotRange;
import ru.vk.itmo.scheduler.slotwise.domain.TimeGrid;
import ru.vk.itmo.scheduler.slotwise.domain.Weights;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Seeded synthetic datasets: the same profile and seed always give the same data.
 *
 * <p>One generated university feeds both outputs: {@link #problem} for solvers and experiments,
 * {@link #university} for the demo import. The data is plausible, not guaranteed to be solvable:
 * finding out how much fits is the job of the solvers and the experiment.
 */
public final class DatasetGenerator {
    public static final String DEMO_PASSWORD = "demo-password";
    /** Precomputed: bcrypt itself lives in Spring Security, which datagen may not depend on. */
    static final String DEMO_PASSWORD_HASH = "{bcrypt}$2a$10$QvjLeKmVYdg3ZIuomgvAhuiziwwWWJyHCgP9EEcRZpt2OubAv6AUG";

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    /** A Monday of a June exam session. */
    private static final LocalDate FIRST_DAY = LocalDate.of(2027, 6, 14);
    private static final LocalTime DAY_START = LocalTime.of(9, 0);
    private static final Duration WORKING_DAY = Duration.ofHours(10);
    /** ITMO class start times, 90 minutes each. */
    private static final List<LocalTime> CLASS_STARTS = List.of(LocalTime.of(8, 20), LocalTime.of(10, 0),
            LocalTime.of(11, 40), LocalTime.of(13, 30), LocalTime.of(15, 20), LocalTime.of(17, 0), LocalTime.of(18, 40));
    private static final Duration CLASS = Duration.ofMinutes(90);

    private static final int STUDENTS_PER_GROUP = 25;
    private static final int COMMISSION_SIZE = 3;
    private static final int EXAM_PREPARATION_DAYS = 2;
    private static final int DEFENSE_GAP_DAYS = 3;
    private static final Duration ROOM_CHANGE_BUFFER = Duration.ofMinutes(15);
    private static final long PROJECTOR = 1;
    private static final long COMPUTERS = 2;
    private static final List<Integer> CAPACITIES = List.of(20, 30, 40, 60, 100);
    private static final List<String> BUILDINGS = List.of("Кронверкский 49", "Ломоносова 9", "Биржевая 14");
    private static final List<String> DISCIPLINES = List.of("Базы данных", "Операционные системы", "Компьютерные сети",
            "Алгоритмы и структуры данных", "Теория вероятностей", "Машинное обучение", "Распределённые системы",
            "Компиляторы", "Информационная безопасность", "Архитектура ЭВМ");
    private static final List<String> MALE_NAMES = List.of("Александр", "Дмитрий", "Максим", "Сергей", "Андрей",
            "Алексей", "Артём", "Илья", "Кирилл", "Михаил");
    private static final List<String> FEMALE_NAMES = List.of("Анна", "Мария", "Елена", "Дарья", "Алина", "Ирина",
            "Екатерина", "Полина", "Ольга", "Софья");
    private static final List<String> SURNAMES = List.of("Иванов", "Смирнов", "Кузнецов", "Попов", "Васильев",
            "Петров", "Соколов", "Михайлов", "Новиков", "Фёдоров", "Морозов", "Волков");
    private static final List<String> PATRONYMICS = List.of("Александров", "Дмитриев", "Сергеев", "Андреев",
            "Алексеев", "Михайлов", "Игорев", "Олегов");

    private final DatasetProfile profile;
    private final String demoDatasetKey;
    private final RandomGenerator random;
    private final List<LocalDate> days = new ArrayList<>();
    private final TimeGrid grid;

    // Account ids: teachers 1..T, then students. Room ids: physical 1..R, then the online platform.
    private final List<Long> teachers = new ArrayList<>();
    private final List<List<Long>> groups = new ArrayList<>();
    private final Map<Long, List<SlotRange>> classes = new TreeMap<>();
    private final Map<Long, List<Person.Busy>> personalBusy = new TreeMap<>();
    private final List<Room> rooms = new ArrayList<>();
    private final List<Exam> exams = new ArrayList<>();
    private final Map<Long, Long> supervisors = new TreeMap<>();

    private record Exam(int group, int discipline, long teacher) {
    }

    private DatasetGenerator(DatasetProfile profile, long seed) {
        this.profile = profile;
        demoDatasetKey = "demo-" + profile.name().toLowerCase(Locale.ROOT) + "-" + seed;
        // A named algorithm gives the same sequence on every JDK.
        this.random = RandomGeneratorFactory.of("L64X128MixRandom").create(seed);
        for (LocalDate day = FIRST_DAY; days.size() < profile.days(); day = day.plusDays(1)) {
            if (day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY) {
                days.add(day);
            }
        }
        Instant origin = FIRST_DAY.atStartOfDay(ZONE).toInstant();
        Instant end = days.getLast().plusDays(1).atStartOfDay(ZONE).toInstant();
        this.grid = new TimeGrid(origin, ZONE, TimeGrid.DEFAULT_SLOT_MINUTES,
                Math.toIntExact(Duration.between(origin, end).toMinutes() / TimeGrid.DEFAULT_SLOT_MINUTES),
                days.stream().map(day -> SlotRange.ofLength(slotOf(origin, day.atTime(DAY_START).atZone(ZONE).toInstant()),
                        Math.toIntExact(WORKING_DAY.toMinutes() / TimeGrid.DEFAULT_SLOT_MINUTES))).toList());
        generatePeople();
        generateRooms();
        generateTeachingAndSupervision();
    }

    /**
     * Solver input: the session over the generated university with its exams, consultations,
     * pre-defenses and defenses.
     */
    public static Problem problem(DatasetProfile profile, long seed) {
        return new DatasetGenerator(profile, seed).toProblem();
    }

    /**
     * University data for the demo import. External IDs and emails identify the profile and seed,
     * so datasets can coexist. Every account has the password {@link #DEMO_PASSWORD}.
     */
    public static UniversityData university(DatasetProfile profile, long seed) {
        return new DatasetGenerator(profile, seed).toUniversity();
    }

    private void generatePeople() {
        long id = 1;
        for (int i = 0; i < profile.teachers(); i++) {
            teachers.add(id);
            classes.put(id, dailyClasses());
            personalBusy.put(id++, personalBusy());
        }
        for (int g = 0; g < profile.groups(); g++) {
            List<SlotRange> groupClasses = dailyClasses();
            List<Long> students = new ArrayList<>();
            for (int i = 0; i < STUDENTS_PER_GROUP; i++) {
                students.add(id);
                classes.put(id, groupClasses);
                personalBusy.put(id++, personalBusy());
            }
            groups.add(students);
        }
    }

    private void generateRooms() {
        for (int i = 0; i < profile.rooms(); i++) {
            // The first two rooms are big enough for a group and have a projector, so every event has a room.
            int capacity = i == 0 ? 60 : i == 1 ? 30 : pick(CAPACITIES);
            List<Long> equipment = new ArrayList<>();
            if (i < 2 || random.nextDouble() < 0.7) {
                equipment.add(PROJECTOR);
            }
            if (random.nextDouble() < 0.3) {
                equipment.add(COMPUTERS);
            }
            rooms.add(new Room(i + 1, capacity, equipment, dailyClasses()));
        }
        rooms.add(new Room(profile.rooms() + 1, null, List.of(), List.of(), true));
    }

    private void generateTeachingAndSupervision() {
        for (int g = 0; g < profile.groups(); g++) {
            for (int e = 0; e < profile.examsPerGroup(); e++) {
                exams.add(new Exam(g, (g * profile.examsPerGroup() + e) % DISCIPLINES.size(), pick(teachers)));
            }
        }
        groups.stream().flatMap(List::stream).limit(profile.defenses())
                .forEach(student -> supervisors.put(student, pick(teachers)));
    }

    private Problem toProblem() {
        Problem.ProblemBuilder problem = Problem.builder()
                .grid(grid)
                .rooms(rooms)
                .roomChangeBuffer(grid.toSlots(ROOM_CHANGE_BUFFER))
                .weights(Weights.DEFAULT);
        classes.forEach((id, timetable) -> {
            List<Person.Busy> busy = new ArrayList<>(personalBusy.get(id));
            timetable.forEach(range -> busy.add(new Person.Busy(range, Person.Busy.Kind.TIMETABLE)));
            problem.person(new Person(id, teachers.contains(id) ? Person.Role.TEACHER : Person.Role.STUDENT, busy));
        });
        for (int i = 0; i < teachers.size(); i++) {
            problem.commissionCandidate(new CommissionCandidate(teachers.get(i), i % 3 == 0));
        }

        long eventId = 1;
        for (Exam exam : exams) {
            List<Long> participants = new ArrayList<>(groups.get(exam.group()));
            participants.add(exam.teacher());
            Event.Format consultationFormat = random.nextDouble() < 0.2 ? Event.Format.ONLINE : Event.Format.ONSITE;
            long consultation = eventId++;
            long examId = eventId++;
            problem.event(groupEvent(consultation, Duration.ofMinutes(60), consultationFormat, participants, 0));
            problem.event(groupEvent(examId, Duration.ofMinutes(90), Event.Format.ONSITE, participants, EXAM_PREPARATION_DAYS));
            problem.dependency(Dependency.between(consultation, examId, 1, 2));
        }
        // Short datasets cannot fit three days between a pre-defense and a defense.
        int defenseGap = Math.min(DEFENSE_GAP_DAYS, profile.days() - 1);
        for (Map.Entry<Long, Long> supervision : supervisors.entrySet()) {
            List<Long> participants = List.of(supervision.getKey(), supervision.getValue());
            Event.Format preDefenseFormat = random.nextDouble() < 0.3 ? Event.Format.ONLINE : Event.Format.ONSITE;
            long preDefense = eventId++;
            long defense = eventId++;
            problem.event(defenseEvent(preDefense, Duration.ofMinutes(20), preDefenseFormat, participants));
            problem.event(defenseEvent(defense, Duration.ofMinutes(30), Event.Format.ONSITE, participants));
            problem.dependency(Dependency.atLeast(preDefense, defense, defenseGap));
        }
        return problem.build();
    }

    private Event groupEvent(long id, Duration duration, Event.Format format, List<Long> participants, int preparationDays) {
        return new Event(id, grid.toSlots(duration), Event.Scope.GROUP, format, participants,
                Event.CommissionRequirement.NONE, List.of(), List.of(), preparationDays);
    }

    private Event defenseEvent(long id, Duration duration, Event.Format format, List<Long> studentAndSupervisor) {
        List<Long> equipment = format == Event.Format.ONLINE ? List.of() : List.of(PROJECTOR);
        return new Event(id, grid.toSlots(duration), Event.Scope.INDIVIDUAL, format, studentAndSupervisor,
                new Event.CommissionRequirement(COMMISSION_SIZE, true), List.of(studentAndSupervisor.get(1)), equipment, 0);
    }

    private UniversityData toUniversity() {
        List<UniversityData.Account> accounts = new ArrayList<>();
        String organizerId = demoId("organizer");
        accounts.add(new UniversityData.Account(organizerId, demoEmail(organizerId), "Организатор", "Демо",
                null, UniversityData.Role.ORGANIZER, ZONE.getId(), DEMO_PASSWORD_HASH));
        classes.keySet().forEach(id -> accounts.add(account(id)));

        List<UniversityData.Group> groupData = new ArrayList<>();
        for (int g = 0; g < groups.size(); g++) {
            groupData.add(new UniversityData.Group(groupId(g), groupId(g), groups.get(g).stream().map(this::accountId).toList()));
        }
        List<UniversityData.Room> roomData = rooms.stream().map(room -> room.online()
                ? new UniversityData.Room(demoId("room-online"), demoName("Онлайн-площадка"), null, true, null, List.of())
                : new UniversityData.Room(roomId(room.id()), demoName(String.valueOf(1100 + 100 * (int) (room.id() % 4) + room.id())),
                BUILDINGS.get((int) (room.id() % BUILDINGS.size())), false, room.capacity(),
                room.equipment().stream().map(e -> e == PROJECTOR ? "Проектор" : "Компьютеры").toList())).toList();
        List<UniversityData.Discipline> disciplines = exams.stream().map(Exam::discipline).distinct().sorted()
                .map(d -> new UniversityData.Discipline(disciplineId(d), demoName(DISCIPLINES.get(d)))).toList();
        List<UniversityData.Teaching> teaching = exams.stream().map(exam -> new UniversityData.Teaching(
                accountId(exam.teacher()), disciplineId(exam.discipline()), groupId(exam.group()))).distinct().toList();
        List<UniversityData.Supervision> supervision = supervisors.entrySet().stream().map(entry ->
                new UniversityData.Supervision(accountId(entry.getKey()), accountId(entry.getValue()))).toList();

        List<UniversityData.TimetableEntry> timetable = new ArrayList<>();
        classes.forEach((id, ranges) -> ranges.forEach(range -> timetable.add(new UniversityData.TimetableEntry(
                accountId(id), null, grid.toInstant(range.start()), grid.toInstant(range.end())))));
        rooms.forEach(room -> room.busy().forEach(range -> timetable.add(new UniversityData.TimetableEntry(
                null, roomId(room.id()), grid.toInstant(range.start()), grid.toInstant(range.end())))));
        UniversityData.SessionHint session = new UniversityData.SessionHint(days.getFirst(), days.getLast(), ZONE,
                DAY_START, DAY_START.plus(WORKING_DAY));
        return new UniversityData(session, accounts, groupData, roomData, disciplines, teaching, supervision, timetable);
    }

    private UniversityData.Account account(long id) {
        boolean teacher = teachers.contains(id);
        boolean female = random.nextBoolean();
        String surname = pick(SURNAMES) + (female ? "а" : "");
        String patronymic = pick(PATRONYMICS) + (female ? "на" : "ич");
        String externalId = accountId(id);
        return new UniversityData.Account(externalId, demoEmail(externalId), surname,
                pick(female ? FEMALE_NAMES : MALE_NAMES), patronymic,
                teacher ? UniversityData.Role.TEACHER : UniversityData.Role.STUDENT, ZONE.getId(), DEMO_PASSWORD_HASH);
    }

    private String accountId(long id) {
        // Locale.ROOT: with the default locale some JVMs print non-ASCII digits, and the import matches by these ids.
        return demoId(String.format(Locale.ROOT, teachers.contains(id) ? "t%03d" : "s%04d", id));
    }

    private String groupId(int group) {
        return demoId("P34" + (10 + group));
    }

    private String roomId(long id) {
        return demoId("room-" + id);
    }

    private String disciplineId(int discipline) {
        return demoId("discipline-" + (discipline + 1));
    }

    private String demoId(String localId) {
        return demoDatasetKey + "-" + localId;
    }

    private String demoName(String name) {
        return name + " (" + demoDatasetKey + ")";
    }

    private static String demoEmail(String externalId) {
        return externalId + "@demo.slotwise.ru";
    }

    /** Up to one class a day in an exam session, up to three in the tight profile. */
    private List<SlotRange> dailyClasses() {
        List<SlotRange> result = new ArrayList<>();
        int max = profile.tight() ? 3 : 1;
        for (LocalDate day : days) {
            List<LocalTime> starts = new ArrayList<>(CLASS_STARTS);
            for (int n = random.nextInt(max + 1); n > 0; n--) {
                result.add(range(day, starts.remove(random.nextInt(starts.size())), CLASS));
            }
        }
        return result;
    }

    /** One to three hours of personal busy time inside the working day; more of it in the tight profile. */
    private List<Person.Busy> personalBusy() {
        List<Person.Busy> result = new ArrayList<>();
        for (int n = random.nextInt(profile.tight() ? 5 : 3); n > 0; n--) {
            Duration length = Duration.ofMinutes(60 + 30L * random.nextInt(5));
            long latestStart = WORKING_DAY.minus(length).toMinutes() / TimeGrid.DEFAULT_SLOT_MINUTES;
            LocalTime start = DAY_START.plusMinutes(TimeGrid.DEFAULT_SLOT_MINUTES * random.nextLong(latestStart + 1));
            double kind = random.nextDouble();
            result.add(new Person.Busy(range(pick(days), start, length),
                    kind < 0.3 ? Person.Busy.Kind.CRITICAL : kind < 0.7 ? Person.Busy.Kind.MEDIUM : Person.Busy.Kind.LOW));
        }
        return result;
    }

    private SlotRange range(LocalDate day, LocalTime start, Duration length) {
        Instant from = day.atTime(start).atZone(ZONE).toInstant();
        return grid.covering(from, from.plus(length)).orElseThrow();
    }

    private static int slotOf(Instant origin, Instant instant) {
        return Math.toIntExact(Duration.between(origin, instant).toMinutes() / TimeGrid.DEFAULT_SLOT_MINUTES);
    }

    private <T> T pick(List<T> items) {
        return items.get(random.nextInt(items.size()));
    }
}
