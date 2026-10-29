package ru.vk.itmo.scheduler.slotwise.model.university;

import org.hibernate.annotations.DynamicInsert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "timetable_entry", schema = "slotwise")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TimetableEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "external_id", length = 255)
    private String externalId;

    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "room_id")
    private Long roomId;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "description", columnDefinition = "text")
    private String description;
}
