package ru.vk.itmo.scheduler.slotwise.solving;

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
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SourceType;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "solver_job")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SolverJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long planningSessionId;
    private Long inputSnapshotId;

    @Enumerated(EnumType.STRING)
    private SolverAlgorithm algorithm;

    @JdbcTypeCode(SqlTypes.JSON)
    @Generated(event = EventType.INSERT, writable = true)
    private String parameters;

    @Generated(event = EventType.INSERT, writable = true)
    @Enumerated(EnumType.STRING)
    private SolverJobStatus status;

    private Long requestedBy;

    @CreationTimestamp(source = SourceType.DB)
    @Column(updatable = false)
    private Instant createdAt;

    private Instant startedAt;
    private Instant finishedAt;
    private String errorMessage;

    @Version
    private Long version;
}
