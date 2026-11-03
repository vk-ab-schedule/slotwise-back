package ru.vk.itmo.scheduler.slotwise.request;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.SourceType;
import org.hibernate.generator.EventType;

import java.time.Instant;

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "change_request")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChangeRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long planningSessionId;
    private Long requesterId;
    private Long eventId;
    private Instant startsAt;
    private Instant endsAt;
    private String reason;

    @Generated(event = EventType.INSERT, writable = true)
    @Enumerated(EnumType.STRING)
    private ChangeRequestStatus status;

    private String decisionComment;
    private Long decidedBy;
    private Instant decidedAt;

    @CreationTimestamp(source = SourceType.DB)
    @Column(updatable = false)
    private Instant createdAt;

    @Version
    private Long version;
}
