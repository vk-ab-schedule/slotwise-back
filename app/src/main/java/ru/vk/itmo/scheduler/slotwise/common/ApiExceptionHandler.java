package ru.vk.itmo.scheduler.slotwise.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The single place where exceptions become Problem Details (RFC 9457).
 *
 * <p>Besides the standard fields, every problem raised here has a stable {@code code} for the frontend.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuthentication(AuthenticationException e) {
        return switch (e) {
            case BadCredentialsException _ ->
                    problem(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Неверный email или пароль");
            case DisabledException _ ->
                    problem(HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED", "Учётная запись отключена, обратитесь к администратору");
            default -> problem(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Нужно войти в систему");
        };
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException e) {
        return problem(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Недостаточно прав");
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
