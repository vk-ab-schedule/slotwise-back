package ru.vk.itmo.scheduler.slotwise.model.solving;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.Immutable;
import java.util.Objects;
import org.hibernate.annotations.SourceType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@DynamicInsert
@Table(name = "input_snapshot", schema = "slotwise")
@Immutable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InputSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "planning_session_id", updatable = false)
    private Long planningSessionId;

    @Column(name = "input_version", updatable = false)
    private Long inputVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "problem", columnDefinition = "jsonb", updatable = false)
    private String problem;

    @CreationTimestamp(source = SourceType.DB)
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public static InputSnapshotEntity create(long planningSessionId, long inputVersion, String problem) {
        if (planningSessionId <= 0 || inputVersion < 0) {
            throw new IllegalArgumentException("Session ID must be positive and input version non-negative");
        }
        InputSnapshotEntity snapshot = new InputSnapshotEntity();
        snapshot.planningSessionId = planningSessionId;
        snapshot.inputVersion = inputVersion;
        snapshot.problem = Objects.requireNonNull(problem, "problem");
        return snapshot;
    }
}
