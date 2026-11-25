package ru.vk.itmo.scheduler.slotwise.account;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.vk.itmo.scheduler.slotwise.account.api.dto.AccountResponse;
import ru.vk.itmo.scheduler.slotwise.account.api.dto.CreateAccountRequest;
import ru.vk.itmo.scheduler.slotwise.account.api.dto.SetAccountEnabledRequest;
import ru.vk.itmo.scheduler.slotwise.account.api.dto.SetAccountPasswordRequest;
import ru.vk.itmo.scheduler.slotwise.account.api.dto.SetAccountRoleRequest;
import ru.vk.itmo.scheduler.slotwise.account.api.dto.UpdateAccountRequest;
import ru.vk.itmo.scheduler.slotwise.common.ApiException;
import ru.vk.itmo.scheduler.slotwise.common.api.PageResponse;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Account management by an administrator (UX-02, UX-03, accounts in UX-23).
 *
 * <p>A login session keeps the role it was created with, so a role change or disabling ends all sessions
 * of the account, and a new password ends all of them except the caller's own.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AccountService {
    private static final Sort ORDER = Sort.by("lastName", "firstName", "id");

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> find(String search, AccountRole role, Boolean enabled, int page, int size) {
        String pattern = search == null || search.isBlank() ? null
                : "%" + search.strip().toLowerCase(Locale.ROOT).replaceAll("[\\\\%_]", "\\\\$0") + "%";
        Page<AccountEntity> found = accounts.search(pattern, role, enabled, PageRequest.of(page, size, ORDER));
        return new PageResponse<>(found.map(AccountResponse::of).getContent(), found.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public AccountResponse get(long id) {
        return AccountResponse.of(account(id));
    }

    public AccountResponse create(CreateAccountRequest request) {
        var account = new AccountEntity();
        account.setRole(request.role());
        account.setEnabled(true);
        setProfile(account, request.externalId(), request.email(), request.lastName(), request.firstName(),
                request.patronymic(), request.timeZone());
        return AccountResponse.of(accounts.saveAndFlush(account));
    }

    public AccountResponse update(long id, UpdateAccountRequest request) {
        var account = account(id, request.expectedVersion());
        setProfile(account, request.externalId(), request.email(), request.lastName(), request.firstName(),
                request.patronymic(), request.timeZone());
        return AccountResponse.of(accounts.saveAndFlush(account));
    }

    public AccountResponse setRole(long id, long actorId, SetAccountRoleRequest request) {
        if (id == actorId) {
            throw new ApiException(HttpStatus.FORBIDDEN, "OWN_ROLE_CHANGE",
                    "Свою роль может изменить только другой администратор");
        }
        var account = account(id, request.expectedVersion());
        if (account.getRole() == request.role()) {
            return AccountResponse.of(account);
        }
        if (account.getRole() == AccountRole.ADMIN) {
            requireAnotherActiveAdmin(id);
        }
        account.setRole(request.role());
        endSessions(id);
        return AccountResponse.of(accounts.saveAndFlush(account));
    }

    public AccountResponse setEnabled(long id, SetAccountEnabledRequest request) {
        var account = account(id, request.expectedVersion());
        if (account.getEnabled().equals(request.enabled())) {
            return AccountResponse.of(account);
        }
        if (!request.enabled()) {
            if (account.getRole() == AccountRole.ADMIN) {
                requireAnotherActiveAdmin(id);
            }
            endSessions(id);
        }
        account.setEnabled(request.enabled());
        return AccountResponse.of(accounts.saveAndFlush(account));
    }

    public void setPassword(long id, long actorId, String actorSessionId, SetAccountPasswordRequest request) {
        var account = account(id, request.expectedVersion());
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        accounts.saveAndFlush(account);
        endSessionsAfterCommit(id, id == actorId ? actorSessionId : null);
    }

    /**
     * Ends all login sessions of the account once the current transaction commits, for example after its role
     * was changed by an import: until then a concurrent login would still get the old role.
     */
    public void endSessions(long accountId) {
        endSessionsAfterCommit(accountId, null);
    }

    private void endSessionsAfterCommit(long accountId, String keepSessionId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // Spring Session deletes in its own transaction, so this runs after the change is visible.
                sessions.findByPrincipalName(Long.toString(accountId)).keySet().stream()
                        .filter(sessionId -> !sessionId.equals(keepSessionId))
                        .forEach(sessions::deleteById);
            }
        });
    }

    private void setProfile(AccountEntity account, String externalId, String email, String lastName,
                            String firstName, String patronymic, String timeZone) {
        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        String normalizedExternalId = externalId == null || externalId.isBlank() ? null : externalId.strip();
        accounts.findByEmailIgnoringCase(normalizedEmail)
                .filter(other -> !Objects.equals(other.getId(), account.getId()))
                .ifPresent(other -> {
                    throw taken("EMAIL_TAKEN", "Этот email уже у другой учётной записи", other);
                });
        if (normalizedExternalId != null) {
            accounts.findByExternalId(normalizedExternalId)
                    .filter(other -> !Objects.equals(other.getId(), account.getId()))
                    .ifPresent(other -> {
                        throw taken("EXTERNAL_ID_TAKEN", "Этот внешний идентификатор уже у другой учётной записи", other);
                    });
        }
        account.setExternalId(normalizedExternalId);
        account.setEmail(normalizedEmail);
        account.setLastName(lastName.strip());
        account.setFirstName(firstName.strip());
        account.setPatronymic(patronymic == null || patronymic.isBlank() ? null : patronymic.strip());
        account.setTimeZone(timeZone);
    }

    private void requireAnotherActiveAdmin(long id) {
        if (accounts.findByRoleAndEnabledTrue(AccountRole.ADMIN).stream().allMatch(admin -> admin.getId() == id)) {
            throw new ApiException(HttpStatus.CONFLICT, "LAST_ACTIVE_ADMIN",
                    "В системе должен остаться хотя бы один активный администратор");
        }
    }

    private AccountEntity account(long id) {
        return accounts.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Учётная запись не найдена"));
    }

    private AccountEntity account(long id, long expectedVersion) {
        var account = account(id);
        if (account.getVersion() != expectedVersion) {
            throw new ApiException(HttpStatus.CONFLICT, "STALE_ACCOUNT_VERSION",
                    "Учётная запись изменилась; обновите данные и повторите запрос");
        }
        return account;
    }

    private static ApiException taken(String code, String message, AccountEntity existing) {
        return new ApiException(HttpStatus.CONFLICT, code, message, Map.of("accountId", existing.getId()));
    }
}
