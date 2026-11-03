package ru.vk.itmo.scheduler.slotwise.storage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import ru.vk.itmo.scheduler.slotwise.TestcontainersConfiguration;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Schema changes of the constraint model revision (D-16, migration V7).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ConstraintModelSchemaTests {
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void durationsAreMultiplesOfFiveMinutes() {
        jdbc.update("""
                insert into slotwise.event_type(name, default_duration_minutes, granularity, default_format)
                values ('Защита 20 минут', 20, 'PER_STUDENT', 'ONSITE')
                """);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                insert into slotwise.event_type(name, default_duration_minutes, granularity, default_format)
                values ('Защита 12 минут', 12, 'PER_STUDENT', 'ONSITE')
                """));
    }

    @Test
    void examNeedsTwoPreparationDaysAndOthersNone() {
        assertEquals(2, preparationDays("Экзамен по дисциплине"));
        assertEquals(0, preparationDays("Защита"));
    }

    @Test
    void consultationIsOneOrTwoDaysBeforeExam() {
        Map<String, Object> gap = jdbc.queryForMap("""
                select d.min_gap_days, d.max_gap_days from slotwise.event_type_dependency d
                join slotwise.event_type p on p.id = d.predecessor_type_id
                join slotwise.event_type s on s.id = d.successor_type_id
                where p.name = 'Консультация по дисциплине' and s.name = 'Экзамен по дисциплине'
                """);
        assertEquals(1, gap.get("min_gap_days"));
        assertEquals(2, gap.get("max_gap_days"));
    }

    @Test
    void maximumGapIsOptionalButNotBelowMinimum() {
        assertNull(jdbc.queryForObject("""
                select d.max_gap_days from slotwise.event_type_dependency d
                join slotwise.event_type p on p.id = d.predecessor_type_id
                where p.name = 'Предзащита'
                """, Integer.class));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                update slotwise.event_type_dependency set max_gap_days = min_gap_days - 1
                """));
    }

    private int preparationDays(String type) {
        return jdbc.queryForObject("select preparation_days from slotwise.event_type where name = ?", Integer.class, type);
    }
}
