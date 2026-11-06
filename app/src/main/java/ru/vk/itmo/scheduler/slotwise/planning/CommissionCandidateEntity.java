package ru.vk.itmo.scheduler.slotwise.planning;

import jakarta.persistence.Entity;
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

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "commission_candidate")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(CommissionCandidateEntity.Key.class)
public class CommissionCandidateEntity {

    @Id
    private Long planningSessionId;

    @Id
    private Long accountId;

    @Generated(event = EventType.INSERT, writable = true)
    private Boolean canChair;

    @Data
    public static class Key implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long planningSessionId;
        private Long accountId;
    }
}
