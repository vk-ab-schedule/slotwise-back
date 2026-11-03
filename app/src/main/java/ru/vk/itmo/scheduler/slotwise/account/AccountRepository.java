package ru.vk.itmo.scheduler.slotwise.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<AccountEntity, Long> {

    /**
     * Emails are unique ignoring case and surrounding spaces (index {@code ux_account_email}).
     */
    @Query("select a from AccountEntity a where lower(trim(a.email)) = lower(trim(:email))")
    Optional<AccountEntity> findByEmailIgnoringCase(String email);

    boolean existsByRole(AccountRole role);
}
