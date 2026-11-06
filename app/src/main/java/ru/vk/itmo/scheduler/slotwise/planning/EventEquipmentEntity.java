package ru.vk.itmo.scheduler.slotwise.planning;

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
@Table(name = "event_equipment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(EventEquipmentEntity.Key.class)
public class EventEquipmentEntity {

    @Id
    private Long eventId;

    @Id
    private Long equipmentId;

    @Data
    public static class Key implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long eventId;
        private Long equipmentId;
    }
}
