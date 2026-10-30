package ru.vk.itmo.scheduler.slotwise.eventtype;

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
@Table(name = "event_type_dependency", schema = "slotwise")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(EventTypeDependencyEntity.Key.class)
public class EventTypeDependencyEntity {

    @Id
    @Column(name = "predecessor_type_id")
    private Long predecessorTypeId;

    @Id
    @Column(name = "successor_type_id")
    private Long successorTypeId;

    @Column(name = "min_gap_days")
    private Integer minGapDays;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Key implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long predecessorTypeId;
        private Long successorTypeId;

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key that)) {
                return false;
            }
            return Objects.equals(predecessorTypeId, that.predecessorTypeId) && Objects.equals(successorTypeId, that.successorTypeId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(predecessorTypeId, successorTypeId);
        }
    }
}
