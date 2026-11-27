package ru.vk.itmo.scheduler.slotwise.schedule;

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
import ru.vk.itmo.scheduler.slotwise.solving.SolveStatus;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "schedule_variant")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleVariantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long planningSessionId;
    private Long inputSnapshotId;
    private Long solverJobId;
    private Long baseVariantId;

    @Generated(event = EventType.INSERT, writable = true)
    @Enumerated(EnumType.STRING)
    private ScheduleVariantStatus status;

    @Enumerated(EnumType.STRING)
    private SolveStatus solveStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Generated(event = EventType.INSERT, writable = true)
    private String metrics;

    private BigDecimal objective;
    private BigDecimal bestBound;
    private Long runtimeMillis;

    @Version
    private Long version;

    @CreationTimestamp(source = SourceType.DB)
    @Column(updatable = false)
    private Instant createdAt;

    private Instant publishedAt;
    private Long publishedBy;
}
