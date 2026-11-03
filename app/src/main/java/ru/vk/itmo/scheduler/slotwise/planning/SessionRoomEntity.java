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
@Table(name = "session_room")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(SessionRoomEntity.Key.class)
public class SessionRoomEntity {

    @Id
    private Long planningSessionId;

    @Id
    private Long roomId;

    @Data
    public static class Key implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long planningSessionId;
        private Long roomId;
    }
}
