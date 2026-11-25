package ru.vk.itmo.scheduler.slotwise.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<AccountEntity, Long> {

    /**
     * Emails are unique ignoring case and surrounding spaces (index {@code ux_account_email}).
     */
    @Query("select a from AccountEntity a where lower(trim(a.email)) = lower(trim(:email))")
    Optional<AccountEntity> findByEmailIgnoringCase(String email);

    Optional<AccountEntity> findByExternalId(String externalId);

    boolean existsByRole(AccountRole role);

    /**
     * {@code pattern} is a lower-case LIKE pattern over the full name and email; null filters match everyone.
     */
    @Query("""
            select a from AccountEntity a
            where (:role is null or a.role = :role)
              and (:enabled is null or a.enabled = :enabled)
              and (:pattern is null or lower(concat(a.lastName, ' ', a.firstName, ' ', coalesce(a.patronymic, ''), ' ',
                   a.email)) like :pattern escape '\\')
            """)
    Page<AccountEntity> search(String pattern, AccountRole role, Boolean enabled, Pageable pageable);

    /**
     * Locks the enabled accounts of the role: two concurrent changes cannot both remove the last administrator.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<AccountEntity> findByRoleAndEnabledTrue(AccountRole role);
}
