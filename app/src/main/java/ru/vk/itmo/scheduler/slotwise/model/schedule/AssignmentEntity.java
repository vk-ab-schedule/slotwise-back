package ru.vk.itmo.scheduler.slotwise.model.schedule;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.time.Instant;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "assignment", schema = "slotwise")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(AssignmentEntity.Key.class)
public class AssignmentEntity {

    @Column(name = "planning_session_id")
    private Long planningSessionId;

    @Id
    @Column(name = "schedule_variant_id")
    private Long scheduleVariantId;

    @Id
    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "status", length = 16)
    private String status;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "room_id")
    private Long roomId;

    @Generated(event = EventType.INSERT, writable = true)
    @Column(name = "locked")
    private Boolean locked;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "explanation", columnDefinition = "jsonb")
    private String explanation;

    @Version
    @Column(name = "version")
    private Long version;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Key implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long scheduleVariantId;
        private Long eventId;

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key that)) return false;
            return Objects.equals(scheduleVariantId, that.scheduleVariantId) && Objects.equals(eventId, that.eventId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(scheduleVariantId, eventId);
        }
    }
}
