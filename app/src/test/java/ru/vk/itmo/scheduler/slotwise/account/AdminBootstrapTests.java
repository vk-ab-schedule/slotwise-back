package ru.vk.itmo.scheduler.slotwise.account;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminBootstrapTests {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    @Test
    void createsAdministratorWhenThereIsNone() {
        when(accounts.findByEmailIgnoringCase("admin@example.org")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("long-password")).thenReturn("{bcrypt}hash");

        bootstrap(" admin@example.org ", "long-password").run(null);

        ArgumentCaptor<AccountEntity> saved = ArgumentCaptor.forClass(AccountEntity.class);
        verify(accounts).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@example.org");
        assertThat(saved.getValue().getRole()).isEqualTo(AccountRole.ADMIN);
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("{bcrypt}hash");
    }

    @Test
    void doesNothingWhenAdministratorExists() {
        when(accounts.existsByRole(AccountRole.ADMIN)).thenReturn(true);

        bootstrap("admin@example.org", "short").run(null);

        verify(accounts, never()).save(any());
    }

    @Test
    void doesNothingWithoutProperties() {
        bootstrap(null, null).run(null);

        verify(accounts, never()).save(any());
    }

    @Test
    void rejectsShortPassword() {
        assertThatIllegalStateException()
                .isThrownBy(() -> bootstrap("admin@example.org", "short").run(null))
                .withMessageContaining("SLOTWISE_ADMIN_PASSWORD");
    }

    @Test
    void doesNotChangeExistingAccountWithAnotherRole() {
        when(accounts.findByEmailIgnoringCase("teacher@example.org")).thenReturn(Optional.of(new AccountEntity()));

        bootstrap("teacher@example.org", "long-password").run(null);

        verify(accounts, never()).save(any());
    }

    private AdminBootstrap bootstrap(String email, String password) {
        return new AdminBootstrap(new AdminProperties(email, password, "Администратор", "SlotWise"), accounts, passwordEncoder);
    }
}
