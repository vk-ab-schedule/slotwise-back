package ru.vk.itmo.scheduler.slotwise.eventtype;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
