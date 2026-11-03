package ru.vk.itmo.scheduler.slotwise.account;

import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.*;
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


