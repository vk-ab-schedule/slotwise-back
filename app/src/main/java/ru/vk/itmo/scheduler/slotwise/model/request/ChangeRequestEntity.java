package ru.vk.itmo.scheduler.slotwise.model.request;

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
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.SourceType;

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "change_request", schema = "slotwise")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChangeRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "planning_session_id")
    private Long planningSessionId;

    @Column(name = "requester_id")
    private Long requesterId;

    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Generated(event = EventType.INSERT, writable = true)
    @Column(name = "status", length = 16)
    private String status;

    @Column(name = "decision_comment", columnDefinition = "text")
    private String decisionComment;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @CreationTimestamp(source = SourceType.DB)
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version")
    private Long version;
}
