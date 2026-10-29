package ru.vk.itmo.scheduler.slotwise.model.account;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import ru.vk.itmo.scheduler.slotwise.TestcontainersConfiguration;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class AccountPersistenceTests {
    @Autowired
    EntityManager entityManager;

    @Test
    void obtainsIdentityAndTimestampsFromPostgres() {
        AccountEntity account = new AccountEntity();
        account.setEmail("jpa-test@example.org");
        account.setFirstName("Иван");
        account.setLastName("Иванов");
        account.setRole("STUDENT");
        assertNull(account.getId());
        entityManager.persist(account);
        entityManager.flush();
        assertNotNull(account.getId());
        assertNotNull(account.getCreatedAt());
        entityManager.refresh(account);
        assertTrue(account.getEnabled());
        assertEquals("Europe/Moscow", account.getTimeZone());
        assertNull(account.getPasswordHash());
    }

    @Test
    void canUpdateNewAccountWithoutRefresh() {
        AccountEntity account = new AccountEntity();
        account.setEmail("defaults-update@example.org");
        account.setFirstName("Иван");
        account.setLastName("Иванов");
        account.setRole("STUDENT");
        entityManager.persist(account);
        entityManager.flush();
        account.setFirstName("Пётр");
        entityManager.flush();
        assertTrue(account.getEnabled());
        assertEquals("Europe/Moscow", account.getTimeZone());
    }

    @Test
    void databaseDefaultsDoNotOverrideExplicitValues() {
        AccountEntity account = new AccountEntity();
        account.setEmail("explicit-defaults@example.org");
        account.setFirstName("Иван");
        account.setLastName("Иванов");
        account.setRole("STUDENT");
        account.setEnabled(false);
        account.setTimeZone("UTC");
        entityManager.persist(account);
        entityManager.flush();
        assertFalse(account.getEnabled());
        assertEquals("UTC", account.getTimeZone());
        entityManager.clear();
        AccountEntity loaded = entityManager.find(AccountEntity.class, account.getId());
        assertFalse(loaded.getEnabled());
        assertEquals("UTC", loaded.getTimeZone());
    }
}
