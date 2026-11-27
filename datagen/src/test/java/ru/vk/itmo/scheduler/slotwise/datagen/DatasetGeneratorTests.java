package ru.vk.itmo.scheduler.slotwise.datagen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ru.vk.itmo.scheduler.slotwise.domain.Dependency;
import ru.vk.itmo.scheduler.slotwise.domain.Event;
import ru.vk.itmo.scheduler.slotwise.domain.Person;
import ru.vk.itmo.scheduler.slotwise.domain.Problem;
import ru.vk.itmo.scheduler.slotwise.domain.Room;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DatasetGeneratorTests {

    @Test
    void sameSeedGivesSameDataset() {
        Problem first = DatasetGenerator.problem(DatasetProfile.M, 42);
        Problem second = DatasetGenerator.problem(DatasetProfile.M, 42);

        assertThat(second.grid()).isEqualTo(first.grid());
        assertThat(second.people()).isEqualTo(first.people());
        assertThat(second.rooms()).isEqualTo(first.rooms());
        assertThat(second.events()).isEqualTo(first.events());
        assertThat(second.dependencies()).isEqualTo(first.dependencies());
        assertThat(DatasetGenerator.university(DatasetProfile.M, 42)).isEqualTo(DatasetGenerator.university(DatasetProfile.M, 42));
    }

    @Test
    void idsDoNotDependOnDefaultLocale() {
        Locale saved = Locale.getDefault();
        try {
            Locale.setDefault(Locale.of("ar", "EG"));
            UniversityData arabic = DatasetGenerator.university(DatasetProfile.S, 42);
            Locale.setDefault(Locale.US);
            assertThat(arabic).isEqualTo(DatasetGenerator.university(DatasetProfile.S, 42));
            assertThat(arabic.accounts()).extracting(UniversityData.Account::externalId)
                    .contains("demo-s-42-t001", "demo-s-42-s0011");
        } finally {
            Locale.setDefault(saved);
        }
    }

    @Test
    void differentSeedsGiveDifferentDatasets() {
        assertThat(DatasetGenerator.problem(DatasetProfile.M, 1).people())
                .isNotEqualTo(DatasetGenerator.problem(DatasetProfile.M, 2).people());
    }

    @Test
    void onlinePlatformsBelongToTheirOwnDemoProfile() {
        var small = DatasetGenerator.university(DatasetProfile.S, 42).rooms();
        var medium = DatasetGenerator.university(DatasetProfile.M, 42).rooms();

        assertThat(small.stream().filter(UniversityData.Room::online).map(UniversityData.Room::externalId).toList())
                .containsExactly("demo-s-42-room-online");
        assertThat(medium.stream().filter(UniversityData.Room::online).map(UniversityData.Room::externalId).toList())
                .containsExactly("demo-m-42-room-online");
        assertThat(Stream.concat(small.stream(), medium.stream()).filter(room -> !room.online()))
                .noneMatch(room -> room.externalId().endsWith("-room-online"));
    }

    @ParameterizedTest
    @EnumSource(DatasetProfile.class)
    void sizesFollowTheProfile(DatasetProfile profile) {
        Problem problem = DatasetGenerator.problem(profile, 7);

        assertThat(problem.events()).hasSize(profile.events());
        assertThat(problem.people()).filteredOn(p -> p.role() == Person.Role.TEACHER).hasSize(profile.teachers());
        assertThat(problem.rooms()).filteredOn(r -> !r.online()).hasSize(profile.rooms());
        assertThat(problem.rooms()).filteredOn(Room::online).hasSize(1);
        assertThat(problem.grid().workingPeriods()).hasSize(profile.days());
        assertThat(problem.commissionPool()).anyMatch(c -> c.canChair());
    }

    @Test
    void examsNeedPreparationAndFollowTheirConsultation() {
        Problem problem = DatasetGenerator.problem(DatasetProfile.L, 3);
        List<Event> exams = problem.events().stream().filter(e -> e.preparationDays() > 0).toList();

        assertThat(exams).hasSize(DatasetProfile.L.groups() * DatasetProfile.L.examsPerGroup())
                .allMatch(e -> e.scope() == Event.Scope.GROUP && e.preparationDays() == 2);
        assertThat(problem.dependencies()).filteredOn(d -> d.maxGapDays().isPresent())
                .hasSize(exams.size())
                .allMatch(d -> d.minGapDays() == 1 && d.maxGapDays().getAsInt() == 2)
                .extracting(Dependency::after).containsExactlyInAnyOrderElementsOf(exams.stream().map(Event::id).toList());
    }

    @Test
    void defensesHaveCommissionWithoutTheSupervisor() {
        Problem problem = DatasetGenerator.problem(DatasetProfile.L, 3);
        List<Event> defenses = problem.events().stream().filter(e -> e.scope() == Event.Scope.INDIVIDUAL).toList();

        assertThat(defenses).hasSize(2 * DatasetProfile.L.defenses()).allSatisfy(e -> {
            assertThat(e.commission()).isEqualTo(new Event.CommissionRequirement(3, true));
            assertThat(e.participants()).hasSize(2).containsAll(e.commissionExcluded());
            assertThat(problem.person(e.commissionExcluded().getFirst()).role()).isEqualTo(Person.Role.TEACHER);
        });
        assertThat(problem.dependencies()).filteredOn(d -> d.maxGapDays().isEmpty())
                .hasSize(DatasetProfile.L.defenses())
                .allMatch(d -> d.minGapDays() == 3);
    }

    @Test
    void shortDatasetKeepsDefenseGapWithinTheSession() {
        assertThat(DatasetGenerator.problem(DatasetProfile.S, 3).dependencies())
                .filteredOn(d -> d.maxGapDays().isEmpty())
                .allMatch(d -> d.minGapDays() == DatasetProfile.S.days() - 1);
    }

    @Test
    void tightDatasetHasLessFreeTime() {
        assertThat(busyMinutes(DatasetGenerator.problem(DatasetProfile.TIGHT, 5)))
                .isGreaterThan(busyMinutes(DatasetGenerator.problem(DatasetProfile.M, 5)));
    }

    @Test
    void universityDataReferencesOnlyItsOwnRecords() {
        UniversityData data = DatasetGenerator.university(DatasetProfile.M, 11);
        Set<String> accounts = ids(data.accounts().stream().map(UniversityData.Account::externalId));
        Set<String> groups = ids(data.groups().stream().map(UniversityData.Group::externalId));
        Set<String> rooms = ids(data.rooms().stream().map(UniversityData.Room::externalId));
        Set<String> disciplines = ids(data.disciplines().stream().map(UniversityData.Discipline::externalId));

        assertThat(data.groups()).flatExtracting(UniversityData.Group::students).allMatch(accounts::contains);
        assertThat(data.teaching()).allMatch(t -> accounts.contains(t.teacher())
                && disciplines.contains(t.discipline()) && groups.contains(t.group()));
        assertThat(data.supervision()).hasSize(DatasetProfile.M.defenses())
                .allMatch(s -> accounts.contains(s.student()) && accounts.contains(s.supervisor()));
        assertThat(data.timetable()).allMatch(t -> (t.account() == null) != (t.room() == null)
                && (t.account() == null || accounts.contains(t.account()))
                && (t.room() == null || rooms.contains(t.room())));
    }

    @Test
    void universityDataIsReadyForTheDemo() {
        UniversityData data = DatasetGenerator.university(DatasetProfile.S, 11);
        Problem problem = DatasetGenerator.problem(DatasetProfile.S, 11);

        assertThat(data.accounts()).hasSize(problem.people().size() + 1)
                .allMatch(a -> a.passwordHash().equals(DatasetGenerator.DEMO_PASSWORD_HASH))
                .filteredOn(a -> a.role() == UniversityData.Role.ORGANIZER).hasSize(1);
        assertThat(data.rooms()).filteredOn(UniversityData.Room::online).singleElement()
                .satisfies(room -> assertThat(room.capacity()).isNull());
        List<LocalTime> classStarts = List.of(LocalTime.of(8, 20), LocalTime.of(10, 0), LocalTime.of(11, 40),
                LocalTime.of(13, 30), LocalTime.of(15, 20), LocalTime.of(17, 0), LocalTime.of(18, 40));
        assertThat(data.timetable()).isNotEmpty().allMatch(t ->
                classStarts.contains(t.start().atZone(ZoneId.of("Europe/Moscow")).toLocalTime()));
    }

    @Test
    void demoSuggestsTheSessionTheTimetableCovers() {
        UniversityData data = DatasetGenerator.university(DatasetProfile.M, 11);
        UniversityData.SessionHint session = data.session();

        // M has 5 working days: Monday to Friday.
        assertThat(session.firstDay()).isEqualTo(LocalDate.of(2027, 6, 14));
        assertThat(session.lastDay()).isEqualTo(LocalDate.of(2027, 6, 18));
        assertThat(session.dayStart()).isEqualTo(LocalTime.of(9, 0));
        assertThat(session.dayEnd()).isEqualTo(LocalTime.of(19, 0));
        assertThat(data.timetable()).allMatch(t -> {
            LocalDate day = t.start().atZone(session.timeZone()).toLocalDate();
            return !day.isBefore(session.firstDay()) && !day.isAfter(session.lastDay());
        });
    }

    private static long busyMinutes(Problem problem) {
        return problem.people().stream().flatMap(p -> p.busy().stream())
                .mapToLong(b -> problem.grid().toMinutes(b.range().length())).sum();
    }

    private static Set<String> ids(Stream<String> ids) {
        List<String> list = ids.toList();
        Set<String> unique = new HashSet<>(list);
        assertThat(unique).hasSameSizeAs(list);
        return unique;
    }
}
