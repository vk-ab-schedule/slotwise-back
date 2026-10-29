package ru.vk.itmo.scheduler.slotwise.model.planning;

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

import java.time.Instant;

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "busy_interval", schema = "slotwise")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusyIntervalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "planning_session_id")
    private Long planningSessionId;

    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "importance", length = 16)
    private String importance;

    @Version
    @Column(name = "version")
    private Long version;
}
