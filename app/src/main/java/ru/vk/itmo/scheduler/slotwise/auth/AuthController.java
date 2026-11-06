package ru.vk.itmo.scheduler.slotwise.auth;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.vk.itmo.scheduler.slotwise.account.AccountRepository;

/**
 * Login and current user (UX-01). Logout is {@code POST /api/auth/logout}, handled by Spring Security:
 * it ends the session and answers 204.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
class AuthController {
    private final LoginService loginService;
    private final AccountRepository accounts;

    @Operation(summary = "Log in with email and password, start a session")
    @PostMapping("/login")
    @Transactional(readOnly = true)
    public CurrentUserResponse login(@Valid @RequestBody LoginRequest login,
                                     HttpServletRequest request, HttpServletResponse response) {
        AccountPrincipal principal = loginService.login(login, request, response);
        return currentUser(principal);
    }

    @Operation(summary = "Current user of the session")
    @GetMapping("/me")
    @Transactional(readOnly = true)
    public CurrentUserResponse me(@AuthenticationPrincipal AccountPrincipal principal) {
        return currentUser(principal);
    }

    private CurrentUserResponse currentUser(AccountPrincipal principal) {
        return accounts.findById(principal.getAccountId())
                .map(CurrentUserResponse::of)
                .orElseThrow(() -> new InsufficientAuthenticationException("Account no longer exists"));
    }
}
