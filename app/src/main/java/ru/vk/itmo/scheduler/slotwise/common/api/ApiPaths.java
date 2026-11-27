package ru.vk.itmo.scheduler.slotwise.common.api;

/**
 * Shared route prefixes; compile-time constants usable in mapping annotations.
 */
public final class ApiPaths {

    public static final String API = "/api";

    public static final String AUTH = API + "/auth";
    public static final String ADMIN = API + "/admin";
    public static final String ACCOUNTS = ADMIN + "/accounts";
    public static final String UNIVERSITY = API + "/university";
    public static final String EVENT_TYPES = API + "/event-types";
    public static final String SESSIONS = API + "/sessions";
    public static final String SOLVERS = API + "/solvers";
    public static final String JOBS = API + "/jobs";
    public static final String VARIANTS = API + "/variants";
    public static final String MY_SCHEDULE = API + "/me/schedule";

    private ApiPaths() {
    }
}
