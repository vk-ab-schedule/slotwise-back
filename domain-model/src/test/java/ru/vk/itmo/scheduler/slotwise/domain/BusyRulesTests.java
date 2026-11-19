package ru.vk.itmo.scheduler.slotwise.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusyRulesTests {

    @Test
    void classesAreAlwaysHard() {
        for (Person.Role role : Person.Role.values()) {
            for (Event.Scope scope : Event.Scope.values()) {
                assertThat(BusyRules.isHard(role, Person.Busy.Kind.TIMETABLE, scope)).isTrue();
                assertThat(BusyRules.softCoefficient(role, Person.Busy.Kind.TIMETABLE, scope)).isZero();
            }
        }
    }

    @Test
    void criticalTimeIsSoftOnlyForStudentsInGroupEvents() {
        assertThat(BusyRules.isHard(Person.Role.TEACHER, Person.Busy.Kind.CRITICAL, Event.Scope.GROUP)).isTrue();
        assertThat(BusyRules.isHard(Person.Role.STUDENT, Person.Busy.Kind.CRITICAL, Event.Scope.INDIVIDUAL)).isTrue();
        assertThat(BusyRules.isHard(Person.Role.STUDENT, Person.Busy.Kind.CRITICAL, Event.Scope.GROUP)).isFalse();
        assertThat(BusyRules.softCoefficient(Person.Role.STUDENT, Person.Busy.Kind.CRITICAL, Event.Scope.GROUP)).isEqualTo(10);
    }

    @Test
    void mediumAndLowAreSoftForEverybody() {
        assertThat(BusyRules.softCoefficient(Person.Role.TEACHER, Person.Busy.Kind.MEDIUM, Event.Scope.INDIVIDUAL)).isEqualTo(3);
        assertThat(BusyRules.softCoefficient(Person.Role.STUDENT, Person.Busy.Kind.LOW, Event.Scope.GROUP)).isEqualTo(1);
    }
}
