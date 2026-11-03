package ru.vk.itmo.scheduler.slotwise.university;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;

import java.time.Instant;

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "timetable_entry")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TimetableEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String externalId;
    private Long accountId;
    private Long roomId;
    private Instant startsAt;
    private Instant endsAt;
    private String description;
}
