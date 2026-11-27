package ru.vk.itmo.scheduler.slotwise.solver.cpsat.exception;

public class BudgetExhaustedException extends RuntimeException {
    public BudgetExhaustedException(String message) {
        super(message, null, false, false);
    }
}
