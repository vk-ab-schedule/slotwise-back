package ru.vk.itmo.scheduler.slotwise.account;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * First administrator, created at startup when there is no administrator yet (D-03).
 * Set through {@code SLOTWISE_ADMIN_EMAIL} and {@code SLOTWISE_ADMIN_PASSWORD}.
 */
@ConfigurationProperties("slotwise.admin")
public record AdminProperties(
        String email,
        String password,
        @DefaultValue("Администратор") String firstName,
        @DefaultValue("SlotWise") String lastName) {

    static final int MIN_PASSWORD_LENGTH = 8;

    @Override
    public String toString() {
        return "AdminProperties[email=" + email + ", password=***]";
    }
}
