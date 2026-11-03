package ru.vk.itmo.scheduler.slotwise.schedule;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "assignment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(AssignmentEntity.Key.class)
public class AssignmentEntity {

    private Long planningSessionId;

    @Id
    private Long scheduleVariantId;

    @Id
    private Long eventId;

    @Enumerated(EnumType.STRING)
    private AssignmentStatus status;

    private Instant startsAt;
    private Instant endsAt;
    private Long roomId;

    @Generated(event = EventType.INSERT, writable = true)
    private Boolean locked;

    @JdbcTypeCode(SqlTypes.JSON)
    private String explanation;

    @Version
    private Long version;

    @Data
    public static class Key implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long scheduleVariantId;
        private Long eventId;
    }
}
