package ru.vk.itmo.scheduler.slotwise.common.api;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public final class MultipleOfValidator implements ConstraintValidator<MultipleOf, Integer> {
    private int divisor;

    @Override
    public void initialize(MultipleOf annotation) {
        divisor = annotation.value();
        if (divisor <= 0) {
            throw new IllegalArgumentException("MultipleOf divisor must be positive");
        }
    }

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        return value == null || value % divisor == 0;
    }
}
