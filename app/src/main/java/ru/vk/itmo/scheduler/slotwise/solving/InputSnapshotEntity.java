package ru.vk.itmo.scheduler.slotwise.solving;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

@Entity
@Getter
@DynamicInsert
@Table(name = "input_snapshot")
@Immutable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InputSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(updatable = false)
    private Long planningSessionId;

    @Column(updatable = false)
    private Long inputVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false)
    private String problem;

    @CreationTimestamp(source = SourceType.DB)
    @Column(updatable = false)
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
