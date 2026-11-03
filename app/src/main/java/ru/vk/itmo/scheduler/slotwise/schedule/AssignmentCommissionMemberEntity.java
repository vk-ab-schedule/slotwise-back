package ru.vk.itmo.scheduler.slotwise.schedule;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.io.Serial;
import java.io.Serializable;

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "assignment_commission_member")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(AssignmentCommissionMemberEntity.Key.class)
public class AssignmentCommissionMemberEntity {

    private Long planningSessionId;

    @Id
    private Long scheduleVariantId;

    @Id
    private Long eventId;

    @Id
    private Long accountId;

    @Generated(event = EventType.INSERT, writable = true)
    private Boolean chair;

    @Data
    public static class Key implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long scheduleVariantId;
        private Long eventId;
        private Long accountId;
    }
}
