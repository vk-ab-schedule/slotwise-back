package ru.vk.itmo.scheduler.slotwise.planning;

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
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "session_participant")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(SessionParticipantEntity.Key.class)
public class SessionParticipantEntity {

    @Id
    private Long planningSessionId;

    @Id
    private Long accountId;

    @Generated(event = EventType.INSERT, writable = true)
    @Enumerated(EnumType.STRING)
    private ResponseStatus responseStatus;

    private long calendarRevision;

    private Instant submittedAt;
    private Instant lastScheduleViewedAt;

    @Data
    public static class Key implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long planningSessionId;
        private Long accountId;
    }
}
