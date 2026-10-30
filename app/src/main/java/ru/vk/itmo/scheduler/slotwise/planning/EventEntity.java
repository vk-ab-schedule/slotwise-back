package ru.vk.itmo.scheduler.slotwise.planning;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import ru.vk.itmo.scheduler.slotwise.eventtype.EventFormat;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "event", schema = "slotwise")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "planning_session_id")
    private Long planningSessionId;

    @Column(name = "event_type_id")
    private Long eventTypeId;

    @Column(name = "discipline_id")
    private Long disciplineId;

    @Column(name = "study_group_id")
    private Long studyGroupId;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", length = 16)
    private EventFormat format;

    @Generated(event = EventType.INSERT, writable = true)
    @Column(name = "commission_size")
    private Integer commissionSize;

    @Generated(event = EventType.INSERT, writable = true)
    @Column(name = "requires_chair")
    private Boolean requiresChair;

    @Generated(event = EventType.INSERT, writable = true)
    @Column(name = "complete")
    private Boolean complete;

    @Version
    @Column(name = "version")
    private Long version;
}
