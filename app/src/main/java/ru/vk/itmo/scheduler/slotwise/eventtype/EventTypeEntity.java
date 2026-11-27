package ru.vk.itmo.scheduler.slotwise.eventtype;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "event_type")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventTypeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private Integer defaultDurationMinutes;

    @Enumerated(EnumType.STRING)
    private EventGranularity granularity;

    @Enumerated(EnumType.STRING)
    private EventFormat defaultFormat;

    @Generated(event = EventType.INSERT, writable = true)
    private Integer commissionSize;

    @Generated(event = EventType.INSERT, writable = true)
    private Boolean requiresChair;

    @Generated(event = EventType.INSERT, writable = true)
    private Integer preparationDays;

    @Generated(event = EventType.INSERT, writable = true)
    private Boolean active;

    @Version
    private Long version;
}
