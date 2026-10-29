package ru.vk.itmo.scheduler.slotwise.model.planning;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import org.hibernate.annotations.DynamicInsert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;
import java.time.LocalTime;

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "planning_session", schema = "slotwise")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanningSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "organizer_id")
    private Long organizerId;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Generated(event = EventType.INSERT, writable = true)
    @Column(name = "time_zone", length = 64)
    private String timeZone;

    @Generated(event = EventType.INSERT, writable = true)
    @Column(name = "working_day_start")
    private LocalTime workingDayStart;

    @Generated(event = EventType.INSERT, writable = true)
    @Column(name = "working_day_end")
    private LocalTime workingDayEnd;

    @Column(name = "collection_starts_at")
    private Instant collectionStartsAt;

    @Column(name = "collection_ends_at")
    private Instant collectionEndsAt;

    @Generated(event = EventType.INSERT, writable = true)
    @Column(name = "collection_status", length = 16)
    private String collectionStatus;

    @Column(name = "automatic_solve_requested_at")
    private Instant automaticSolveRequestedAt;

    @Generated(event = EventType.INSERT, writable = true)
    @Column(name = "input_version")
    private Long inputVersion;

    @Version
    @Column(name = "version")
    private Long version;

    @CreationTimestamp(source = SourceType.DB)
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
