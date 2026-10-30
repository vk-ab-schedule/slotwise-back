package ru.vk.itmo.scheduler.slotwise.university;

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
@Table(name = "room", schema = "slotwise")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "external_id", length = 255)
    private String externalId;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "building", length = 255)
    private String building;

    @Generated(event = EventType.INSERT, writable = true)
    @Column(name = "online")
    private Boolean online;

    @Column(name = "capacity")
    private Integer capacity;

    @Generated(event = EventType.INSERT, writable = true)
    @Column(name = "active")
    private Boolean active;

    @Version
    @Column(name = "version")
    private Long version;
}
