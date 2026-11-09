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
import ru.vk.itmo.scheduler.slotwise.common.api.ApiPaths;

/**
 * Login and current user (UX-01). Logout is {@code POST /api/auth/logout}, handled by Spring Security:
 * it ends the session and answers 204.
 */
@RestController
@RequestMapping(value = ApiPaths.AUTH, produces = "application/json")
@RequiredArgsConstructor
class AuthController {
    private final LoginService loginService;
    private final AccountRepository accounts;

    @Operation(operationId = "login", summary = "Войти по email и паролю", description = "Creates SESSION cookie. Requires CSRF; refresh XSRF-TOKEN after login.")
    @PostMapping("/login")
    @Transactional(readOnly = true)
    public CurrentUserResponse login(@Valid @RequestBody LoginRequest login,
                                     HttpServletRequest request, HttpServletResponse response) {
        AccountPrincipal principal = loginService.login(login, request, response);
        return currentUser(principal);
    }

    @Operation(operationId = "getCurrentUser", summary = "Current user", description = "401 without authentication; also initializes the CSRF cookie.")
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
