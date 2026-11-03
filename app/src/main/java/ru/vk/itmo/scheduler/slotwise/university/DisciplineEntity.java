package ru.vk.itmo.scheduler.slotwise.university;

import jakarta.persistence.*;
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
@Table(name = "discipline")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DisciplineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String externalId;
    private String name;

    @Generated(event = EventType.INSERT, writable = true)
    private Boolean active;

    @Version
    private Long version;
}
