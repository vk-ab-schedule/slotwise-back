package ru.vk.itmo.scheduler.slotwise.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vk.itmo.scheduler.slotwise.account.AccountRepository;

@Service
@RequiredArgsConstructor
class AccountUserDetailsService implements UserDetailsService {
    private final AccountRepository accounts;

    /**
     * Looks the account up by email. An account without a password cannot log in and is reported
     * as not found, so the login error does not reveal that the email exists (UX-01).
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return accounts.findByEmailIgnoringCase(email)
                .filter(account -> account.getPasswordHash() != null)
                .map(AccountPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("No account with a password for this email"));
    }
}
