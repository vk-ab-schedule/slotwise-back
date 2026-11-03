package ru.vk.itmo.scheduler.slotwise.eventtype;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;

import java.io.Serial;
import java.io.Serializable;

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "event_type_participant_role")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(EventTypeParticipantRoleEntity.Key.class)
public class EventTypeParticipantRoleEntity {

    @Id
    private Long eventTypeId;

    @Id
    @Enumerated(EnumType.STRING)
    private ParticipantRole participantRole;

    @Data
    public static class Key implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long eventTypeId;
        private ParticipantRole participantRole;
    }
}
