package ru.vk.itmo.scheduler.slotwise.university;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimetableEntryRepository extends JpaRepository<TimetableEntryEntity, Long> {
    List<TimetableEntryEntity> findByAccountIdInAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAt(
            Collection<Long> accountIds, Instant endsAt, Instant startsAt);
}
