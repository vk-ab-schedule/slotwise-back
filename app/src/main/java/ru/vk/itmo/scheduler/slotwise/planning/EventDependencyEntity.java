package ru.vk.itmo.scheduler.slotwise.planning;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;

import java.io.Serial;
import java.io.Serializable;

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "event_dependency")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(EventDependencyEntity.Key.class)
public class EventDependencyEntity {

    private Long planningSessionId;

    @Id
    private Long predecessorEventId;

    @Id
    private Long successorEventId;

    private Integer minGapDays;
    private Integer maxGapDays;

    @Data
    public static class Key implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long predecessorEventId;
        private Long successorEventId;
    }
}
