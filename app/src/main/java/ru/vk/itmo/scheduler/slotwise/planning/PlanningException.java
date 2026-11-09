package ru.vk.itmo.scheduler.slotwise.planning;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class PlanningException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public PlanningException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
