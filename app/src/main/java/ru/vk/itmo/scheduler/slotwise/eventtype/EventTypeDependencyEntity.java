package ru.vk.itmo.scheduler.slotwise.eventtype;

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
@Table(name = "event_type_dependency")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(EventTypeDependencyEntity.Key.class)
public class EventTypeDependencyEntity {

    @Id
    private Long predecessorTypeId;

    @Id
    private Long successorTypeId;

    private Integer minGapDays;
    private Integer maxGapDays;

    @Data
    public static class Key implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long predecessorTypeId;
        private Long successorTypeId;
    }
}
