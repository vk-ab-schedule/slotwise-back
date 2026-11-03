package ru.vk.itmo.scheduler.slotwise.planning;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;

import java.time.Instant;

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "busy_interval")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusyIntervalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long planningSessionId;
    private Long accountId;
    private Instant startsAt;
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    private BusyImportance importance;

    @Version
    private Long version;
}
