package ru.vk.itmo.scheduler.slotwise.domain;

import java.util.Collection;
import java.util.List;

/**
 * Collections of the model are kept sorted, so solvers see the same order on every run (determinism).
 */
final class Ids {
    private Ids() {
    }

    static List<Long> sortedUnique(Collection<Long> ids, String what) {
        List<Long> sorted = ids.stream().sorted().toList();
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).equals(sorted.get(i - 1))) {
                throw new IllegalArgumentException("Duplicate id " + sorted.get(i) + " in " + what);
            }
        }
        return sorted;
    }
}
