package ru.vk.itmo.scheduler.slotwise.common.api;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.ZoneId;
import java.util.Set;

/**
 * IANA time zone name such as {@code Europe/Moscow}; null is valid, combine with {@code @NotBlank}.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = IanaTimeZone.Validator.class)
public @interface IanaTimeZone {
    String message() default "must be an IANA time zone, for example Europe/Moscow";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    final class Validator implements ConstraintValidator<IanaTimeZone, String> {
        private static final Set<String> ZONES = Set.copyOf(ZoneId.getAvailableZoneIds());

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || ZONES.contains(value);
        }
    }
}
