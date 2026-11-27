package ru.vk.itmo.scheduler.slotwise.eventtype;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;

import java.io.Serial;
import java.io.Serializable;

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "event_type_equipment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(EventTypeEquipmentEntity.Key.class)
public class EventTypeEquipmentEntity {

    @Id
    private Long eventTypeId;

    @Id
    private Long equipmentId;

    @Data
    public static class Key implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long eventTypeId;
        private Long equipmentId;
    }
}
