package ru.vk.itmo.scheduler.slotwise.model.schedule;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

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
@Table(name = "assignment_commission_member", schema = "slotwise")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(AssignmentCommissionMemberEntity.Key.class)
public class AssignmentCommissionMemberEntity {

    @Column(name = "planning_session_id")
    private Long planningSessionId;

    @Id
    @Column(name = "schedule_variant_id")
    private Long scheduleVariantId;

    @Id
    @Column(name = "event_id")
    private Long eventId;

    @Id
    @Column(name = "account_id")
    private Long accountId;

    @Generated(event = EventType.INSERT, writable = true)
    @Column(name = "chair")
    private Boolean chair;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Key implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long scheduleVariantId;
        private Long eventId;
        private Long accountId;

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key that)) return false;
            return Objects.equals(scheduleVariantId, that.scheduleVariantId) && Objects.equals(eventId, that.eventId) && Objects.equals(accountId, that.accountId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(scheduleVariantId, eventId, accountId);
        }
    }
}
