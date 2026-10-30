package ru.vk.itmo.scheduler.slotwise.storage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Import;
import ru.vk.itmo.scheduler.slotwise.TestcontainersConfiguration;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class StorageSchemaTests {
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void generatesAccountIdentityAndRejectsDuplicateEmailIgnoringCase() {
        Long id = jdbc.queryForObject("""
                insert into slotwise.account(email, first_name, last_name, role)
                values ('schema-test@example.org', 'Иван', 'Иванов', 'STUDENT') returning id
                """, Long.class);
        assertNotNull(id);
        try {
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update("""
                    insert into slotwise.account(email, first_name, last_name, role)
                    values ('SCHEMA-TEST@example.org', 'Пётр', 'Петров', 'TEACHER')
                    """));
        } finally {
            jdbc.update("delete from slotwise.account where id = ?", id);
        }
    }

    @Test
    void usesSlotwiseSchemaAndSingleRole() {
        assertEquals("ALWAYS", jdbc.queryForObject("""
                select identity_generation from information_schema.columns
                where table_schema='slotwise' and table_name='account' and column_name='id'
                """, String.class));
        assertEquals(0, jdbc.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema='public' and table_name in ('account','event','assignment')
                """, Integer.class));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update("""
                insert into slotwise.account(email, first_name, last_name, role)
                values ('invalid-role@example.org', 'Иван', 'Иванов', 'SUPERUSER')
                """));
    }

    @Test
    void seedsEventTypesAndKeepsAssignmentsSeparateFromEvents() {
        assertEquals(5, jdbc.queryForObject("select count(*) from slotwise.event_type", Integer.class));
        assertEquals(0, jdbc.queryForObject("""
                select count(*) from information_schema.columns where table_schema='slotwise'
                and table_name='event' and column_name in ('starts_at','room_id','locked')
                """, Integer.class));
        assertEquals(3, jdbc.queryForObject("""
                select count(*) from information_schema.tables where table_schema='slotwise'
                and table_name in ('assignment','assignment_commission_member','input_snapshot')
                """, Integer.class));
    }
}
