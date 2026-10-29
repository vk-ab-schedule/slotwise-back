package ru.vk.itmo.scheduler.slotwise.model.eventtype;

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
@Table(name = "event_type_participant_role", schema = "slotwise")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(EventTypeParticipantRoleEntity.Key.class)
public class EventTypeParticipantRoleEntity {

    @Id
    @Column(name = "event_type_id")
    private Long eventTypeId;

    @Id
    @Column(name = "participant_role", length = 24)
    private String participantRole;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Key implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long eventTypeId;
        private String participantRole;

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key that)) {
                return false;
            }
            return Objects.equals(eventTypeId, that.eventTypeId) && Objects.equals(participantRole, that.participantRole);
        }

        @Override
        public int hashCode() {
            return Objects.hash(eventTypeId, participantRole);
        }
    }
}
