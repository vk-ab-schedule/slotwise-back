package ru.vk.itmo.scheduler.slotwise.planning;

import org.springframework.http.HttpStatus;
import ru.vk.itmo.scheduler.slotwise.common.ApiException;

public class PlanningException extends ApiException {

    public PlanningException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }
}
