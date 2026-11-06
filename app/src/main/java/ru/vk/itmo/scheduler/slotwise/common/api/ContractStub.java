package ru.vk.itmo.scheduler.slotwise.common.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public final class ContractStub {
    private ContractStub() {
    }

    public static ErrorResponseException notImplemented() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_IMPLEMENTED,
                "Контракт опубликован; бизнес-операция ещё не реализована.");
        problem.setProperty("code", "NOT_IMPLEMENTED");
        return new ErrorResponseException(HttpStatus.NOT_IMPLEMENTED, problem, null);
    }
}
