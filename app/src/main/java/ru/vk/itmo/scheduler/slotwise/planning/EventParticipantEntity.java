package ru.vk.itmo.scheduler.slotwise.planning;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import ru.vk.itmo.scheduler.slotwise.eventtype.ParticipantRole;

import java.io.Serial;
import java.io.Serializable;

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "event_participant")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(EventParticipantEntity.Key.class)
public class EventParticipantEntity {

    private Long planningSessionId;

    @Id
    private Long eventId;

    @Id
    private Long accountId;

    @Enumerated(EnumType.STRING)
    private ParticipantRole participantRole;

    @Data
    public static class Key implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long eventId;
        private Long accountId;
    }
}
