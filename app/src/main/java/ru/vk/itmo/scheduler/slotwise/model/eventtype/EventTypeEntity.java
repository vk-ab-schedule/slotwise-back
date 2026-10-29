package ru.vk.itmo.scheduler.slotwise.model.eventtype;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "event_type", schema = "slotwise")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventTypeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "default_duration_minutes")
    private Integer defaultDurationMinutes;

    @Column(name = "granularity", length = 16)
    private String granularity;

    @Column(name = "default_format", length = 16)
    private String defaultFormat;

    @Generated(event = EventType.INSERT, writable = true)
    @Column(name = "commission_size")
    private Integer commissionSize;

    @Generated(event = EventType.INSERT, writable = true)
    @Column(name = "requires_chair")
    private Boolean requiresChair;

    @Generated(event = EventType.INSERT, writable = true)
    @Column(name = "active")
    private Boolean active;

    @Version
    @Column(name = "version")
    private Long version;
}
