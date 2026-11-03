package ru.vk.itmo.scheduler.slotwise.auth;

import lombok.Getter;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import ru.vk.itmo.scheduler.slotwise.account.AccountEntity;
import ru.vk.itmo.scheduler.slotwise.account.AccountRole;

import java.io.Serial;
import java.util.Collection;
import java.util.List;

/**
 * Authenticated account stored in the login session.
 *
 * <p>The username is the account id, not the email: Spring Session indexes sessions by it,
 * and the id does not change when an administrator edits the email.
 */
@Getter
public final class AccountPrincipal implements UserDetails, CredentialsContainer {
    @Serial
    private static final long serialVersionUID = 1L;

    private final long accountId;
    private final AccountRole role;
    private final boolean enabled;
    private String password;

    AccountPrincipal(AccountEntity account) {
        this.accountId = account.getId();
        this.role = account.getRole();
        this.enabled = Boolean.TRUE.equals(account.getEnabled());
        this.password = account.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return Long.toString(accountId);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public void eraseCredentials() {
        password = null;
    }
}
