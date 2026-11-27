package ru.vk.itmo.scheduler.slotwise.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * Domain error with a stable {@code code} for the frontend; {@link ApiExceptionHandler} turns it into
 * Problem Details. Properties are added to the problem as they are, for example the id of a conflicting record.
 */
@Getter
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> properties;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public ApiException(HttpStatus status, String code, String message, Map<String, Object> properties) {
        super(message);
        this.status = status;
        this.code = code;
        this.properties = Map.copyOf(properties);
    }
}
