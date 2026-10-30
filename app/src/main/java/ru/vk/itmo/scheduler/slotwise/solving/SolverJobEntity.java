package ru.vk.itmo.scheduler.slotwise.solving;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.SourceType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "solver_job", schema = "slotwise")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SolverJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "planning_session_id")
    private Long planningSessionId;

    @Column(name = "input_snapshot_id")
    private Long inputSnapshotId;

    @Enumerated(EnumType.STRING)
    @Column(name = "algorithm", length = 16)
    private SolverAlgorithm algorithm;

    @JdbcTypeCode(SqlTypes.JSON)
    @Generated(event = EventType.INSERT, writable = true)
    @Column(name = "parameters", columnDefinition = "jsonb")
    private String parameters;

    @Generated(event = EventType.INSERT, writable = true)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16)
    private SolverJobStatus status;

    @Column(name = "requested_by")
    private Long requestedBy;

    @CreationTimestamp(source = SourceType.DB)
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Version
    @Column(name = "version")
    private Long version;
}
