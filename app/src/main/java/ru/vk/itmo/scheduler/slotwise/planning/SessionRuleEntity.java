package ru.vk.itmo.scheduler.slotwise.planning;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;
import ru.vk.itmo.scheduler.slotwise.solving.SolverAlgorithm;

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "session_rule")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionRuleEntity {

    @Id
    private Long planningSessionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Generated(event = EventType.INSERT, writable = true)
    private String weights;

    @Generated(event = EventType.INSERT, writable = true)
    private Integer roomChangeBufferMinutes;

    @Generated(event = EventType.INSERT, writable = true)
    @Enumerated(EnumType.STRING)
    private SolverAlgorithm defaultAlgorithm;

    @Generated(event = EventType.INSERT, writable = true)
    private Integer timeLimitSeconds;

    @Generated(event = EventType.INSERT, writable = true)
    private Long seed;
}
