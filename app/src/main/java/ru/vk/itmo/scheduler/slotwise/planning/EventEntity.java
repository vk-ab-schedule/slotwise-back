package ru.vk.itmo.scheduler.slotwise.planning;

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
import ru.vk.itmo.scheduler.slotwise.eventtype.EventFormat;

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long planningSessionId;
    private Long eventTypeId;
    private Long disciplineId;
    private Long studyGroupId;
    private String title;
    private String description;
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    private EventFormat format;

    @Generated(event = EventType.INSERT, writable = true)
    private Integer commissionSize;

    @Generated(event = EventType.INSERT, writable = true)
    private Boolean requiresChair;

    @Generated(event = EventType.INSERT, writable = true)
    private Integer preparationDays;

    @Generated(event = EventType.INSERT, writable = true)
    private Boolean complete;

    @Version
    private Long version;
}
