package ru.vk.itmo.scheduler.slotwise.model.account;

import jakarta.validation.constraints.Pattern;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.Version;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.Instant;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;
import org.hibernate.generator.EventType;

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "account", schema = "slotwise")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "external_id", length = 255)
    private String externalId;

    @Column(name = "email", length = 320)
    private String email;

    @Pattern(regexp = "\\{[^}]+}.+", message = "Password hash must have the format {algorithm}hash")
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "patronymic", length = 100)
    private String patronymic;

    @Column(name = "role", length = 16)
    private String role;

    @Generated(event = EventType.INSERT, writable = true)
    @Column(name = "time_zone", length = 64)
    private String timeZone;

    @Generated(event = EventType.INSERT, writable = true)
    @Column(name = "enabled")
    private Boolean enabled;

    @Version
    @Column(name = "version")
    private Long version;

    @CreationTimestamp(source = SourceType.DB)
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp(source = SourceType.DB)
    @Column(name = "updated_at")
    private Instant updatedAt;
}


