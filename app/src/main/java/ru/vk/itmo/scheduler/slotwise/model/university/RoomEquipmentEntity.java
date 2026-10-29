package ru.vk.itmo.scheduler.slotwise.model.university;

import org.hibernate.annotations.DynamicInsert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "room_equipment", schema = "slotwise")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(RoomEquipmentEntity.Key.class)
public class RoomEquipmentEntity {

    @Id
    @Column(name = "room_id")
    private Long roomId;

    @Id
    @Column(name = "equipment_id")
    private Long equipmentId;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Key implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long roomId;
        private Long equipmentId;

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key that)) return false;
            return Objects.equals(roomId, that.roomId) && Objects.equals(equipmentId, that.equipmentId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(roomId, equipmentId);
        }
    }
}
