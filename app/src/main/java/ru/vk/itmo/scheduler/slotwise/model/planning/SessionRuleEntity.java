package ru.vk.itmo.scheduler.slotwise.model.planning;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import org.hibernate.annotations.DynamicInsert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "session_rule", schema = "slotwise")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionRuleEntity {

    @Id
    @Column(name = "planning_session_id")
    private Long planningSessionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Generated(event = EventType.INSERT, writable = true)
    @Column(name = "weights", columnDefinition = "jsonb")
    private String weights;

    @Generated(event = EventType.INSERT, writable = true)
    @Column(name = "room_change_buffer_minutes")
    private Integer roomChangeBufferMinutes;

    @Generated(event = EventType.INSERT, writable = true)
    @Column(name = "default_algorithm", length = 16)
    private String defaultAlgorithm;

    @Generated(event = EventType.INSERT, writable = true)
    @Column(name = "time_limit_seconds")
    private Integer timeLimitSeconds;

    @Generated(event = EventType.INSERT, writable = true)
    @Column(name = "seed")
    private Long seed;
}
