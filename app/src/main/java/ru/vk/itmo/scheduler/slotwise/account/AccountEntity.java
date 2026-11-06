package ru.vk.itmo.scheduler.slotwise.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.SourceType;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.generator.EventType;

import java.time.Instant;

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "account")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String externalId;
    private String email;

    @Pattern(regexp = "\\{[^}]+}.+", message = "Password hash must have the format {algorithm}hash")
    private String passwordHash;

    private String firstName;
    private String lastName;
    private String patronymic;

    @Enumerated(EnumType.STRING)
    private AccountRole role;

    @Generated(event = EventType.INSERT, writable = true)
    private String timeZone;

    @Generated(event = EventType.INSERT, writable = true)
    private Boolean enabled;

    @Version
    private Long version;

    @CreationTimestamp(source = SourceType.DB)
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp(source = SourceType.DB)
    private Instant updatedAt;
}


