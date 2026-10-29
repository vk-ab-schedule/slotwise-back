package ru.vk.itmo.scheduler.slotwise.model.planning;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

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
import java.time.Instant;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "session_participant", schema = "slotwise")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(SessionParticipantEntity.Key.class)
public class SessionParticipantEntity {

    @Id
    @Column(name = "planning_session_id")
    private Long planningSessionId;

    @Id
    @Column(name = "account_id")
    private Long accountId;

    @Generated(event = EventType.INSERT, writable = true)
    @Column(name = "response_status", length = 16)
    private String responseStatus;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "last_schedule_viewed_at")
    private Instant lastScheduleViewedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Key implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long planningSessionId;
        private Long accountId;

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key that)) {
                return false;
            }
            return Objects.equals(planningSessionId, that.planningSessionId) && Objects.equals(accountId, that.accountId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(planningSessionId, accountId);
        }
    }
}
