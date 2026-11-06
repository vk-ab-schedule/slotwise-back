package ru.vk.itmo.scheduler.slotwise.common;

import org.jspecify.annotations.NonNull;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.validation.method.ParameterErrors;
import ru.vk.itmo.scheduler.slotwise.common.api.FieldViolation;

import java.util.Objects;

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

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(@NonNull Exception e,
                                                             Object body,
                                                             @NonNull HttpHeaders headers,
                                                             @NonNull HttpStatusCode status,
                                                             @NonNull WebRequest request) {
        ProblemDetail p = body instanceof ProblemDetail detail ? detail
                : e instanceof ErrorResponse error ? error.getBody() : ProblemDetail.forStatusAndDetail(status, "Ошибка обработки запроса");

        if (p.getProperties() == null || !p.getProperties().containsKey("code")) {
            p.setProperty("code", e instanceof MethodArgumentNotValidException || e instanceof HandlerMethodValidationException
                    ? "VALIDATION_FAILED" : status.value() == 501 ? "NOT_IMPLEMENTED" : "INVALID_REQUEST");
        }
        if (e instanceof MethodArgumentNotValidException invalid) {
            p.setProperty("fieldErrors", invalid.getBindingResult().getFieldErrors().stream()
                    .map(error -> new FieldViolation(error.getField(),
                            Objects.requireNonNullElse(error.getDefaultMessage(), "Invalid value"))).toList());
        }
        if (e instanceof HandlerMethodValidationException invalid) {
            p.setProperty("fieldErrors", invalid.getParameterValidationResults().stream().flatMap(result -> {
                if (result instanceof ParameterErrors errors) {
                    return errors.getFieldErrors().stream().map(error -> new FieldViolation(error.getField(),
                            Objects.requireNonNullElse(error.getDefaultMessage(), "Invalid value")));
                }
                return result.getResolvableErrors().stream().map(error -> new FieldViolation(
                        Objects.requireNonNullElse(result.getMethodParameter().getParameterName(), "request"),
                        Objects.requireNonNullElse(error.getDefaultMessage(), "Invalid value")));
            }).toList());
        }
        return super.handleExceptionInternal(e, p, headers, status, request);
    }


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

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e) {
        LOG.error("Unhandled API failure", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Внутренняя ошибка сервера");
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
