package ru.vk.itmo.scheduler.slotwise.planning;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "event_dependency", schema = "slotwise")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(EventDependencyEntity.Key.class)
public class EventDependencyEntity {

    @Column(name = "planning_session_id")
    private Long planningSessionId;

    @Id
    @Column(name = "predecessor_event_id")
    private Long predecessorEventId;

    @Id
    @Column(name = "successor_event_id")
    private Long successorEventId;

    @Column(name = "min_gap_days")
    private Integer minGapDays;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Key implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long predecessorEventId;
        private Long successorEventId;

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key that)) {
                return false;
            }
            return Objects.equals(predecessorEventId, that.predecessorEventId) && Objects.equals(successorEventId, that.successorEventId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(predecessorEventId, successorEventId);
        }
    }
}
