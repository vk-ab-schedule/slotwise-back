package ru.vk.itmo.scheduler.slotwise.account;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Creates the first administrator from deployment parameters if the system has none (UX-02).
 * An existing administrator is never touched, so the variables can stay set after the first start.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(AdminProperties.class)
class AdminBootstrap implements ApplicationRunner {
    private final AdminProperties properties;
    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (accounts.existsByRole(AccountRole.ADMIN)) {
            return;
        }
        if (!StringUtils.hasText(properties.email()) || !StringUtils.hasText(properties.password())) {
            log.warn("There is no administrator. Set SLOTWISE_ADMIN_EMAIL and SLOTWISE_ADMIN_PASSWORD to create one");
            return;
        }
        if (properties.password().length() < AdminProperties.MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("SLOTWISE_ADMIN_PASSWORD must have at least "
                    + AdminProperties.MIN_PASSWORD_LENGTH + " characters");
        }
        String email = properties.email().strip();
        if (accounts.findByEmailIgnoringCase(email).isPresent()) {
            log.warn("There is no administrator, but account {} already exists with another role; not changing it", email);
            return;
        }

        AccountEntity admin = new AccountEntity();
        admin.setEmail(email);
        admin.setFirstName(properties.firstName());
        admin.setLastName(properties.lastName());
        admin.setRole(AccountRole.ADMIN);
        admin.setPasswordHash(passwordEncoder.encode(properties.password()));
        accounts.save(admin);
        log.info("Created the first administrator {}", email);
    }
}
