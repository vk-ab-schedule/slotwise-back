package ru.vk.itmo.scheduler.slotwise.model.schedule;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

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
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.SourceType;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "schedule_variant", schema = "slotwise")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleVariantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "planning_session_id")
    private Long planningSessionId;

    @Column(name = "input_snapshot_id")
    private Long inputSnapshotId;

    @Column(name = "solver_job_id")
    private Long solverJobId;

    @Column(name = "base_variant_id")
    private Long baseVariantId;

    @Generated(event = EventType.INSERT, writable = true)
    @Column(name = "status", length = 16)
    private String status;

    @Column(name = "solve_status", length = 24)
    private String solveStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Generated(event = EventType.INSERT, writable = true)
    @Column(name = "metrics", columnDefinition = "jsonb")
    private String metrics;

    @Column(name = "objective")
    private BigDecimal objective;

    @Column(name = "best_bound")
    private BigDecimal bestBound;

    @Column(name = "runtime_millis")
    private Long runtimeMillis;

    @Version
    @Column(name = "version")
    private Long version;

    @CreationTimestamp(source = SourceType.DB)
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by")
    private Long publishedBy;
}
