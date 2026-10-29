package ru.vk.itmo.scheduler.slotwise.model.account;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountValidationTests {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void closeFactory() {
        FACTORY.close();
    }

    @Test
    void acceptsMissingPasswordHash() {
        assertTrue(VALIDATOR.validateValue(AccountEntity.class, "passwordHash", null).isEmpty());
    }

    @Test
    void acceptsHashWithAlgorithmPrefix() {
        assertTrue(VALIDATOR.validateValue(AccountEntity.class, "passwordHash", "{bcrypt}$2a$10$example").isEmpty());
    }

    @Test
    void rejectsMalformedPasswordHash() {
        for (String value : new String[]{"", "plain-password", "{}hash", "{bcrypt}", "bcrypt}hash"}) {
            assertFalse(VALIDATOR.validateValue(AccountEntity.class, "passwordHash", value).isEmpty(), value);
        }
    }
}
