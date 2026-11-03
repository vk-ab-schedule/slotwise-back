package ru.vk.itmo.scheduler.slotwise.planning;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.SourceType;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.time.LocalTime;

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "planning_session")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanningSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long organizerId;
    private String name;
    private Instant startsAt;
    private Instant endsAt;

    @Generated(event = EventType.INSERT, writable = true)
    private String timeZone;

    @Generated(event = EventType.INSERT, writable = true)
    private LocalTime workingDayStart;

    @Generated(event = EventType.INSERT, writable = true)
    private LocalTime workingDayEnd;

    private Instant collectionStartsAt;
    private Instant collectionEndsAt;

    @Generated(event = EventType.INSERT, writable = true)
    @Enumerated(EnumType.STRING)
    private CollectionStatus collectionStatus;

    private Instant automaticSolveRequestedAt;

    @Generated(event = EventType.INSERT, writable = true)
    private Long inputVersion;

    @Version
    private Long version;

    @CreationTimestamp(source = SourceType.DB)
    @Column(updatable = false)
    private Instant createdAt;
}
