package ru.vk.itmo.scheduler.slotwise.university;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;

import java.io.Serial;
import java.io.Serializable;

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "supervision")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(SupervisionEntity.Key.class)
public class SupervisionEntity {

    @Id
    private Long studentId;

    @Id
    private Long supervisorId;

    @Data
    public static class Key implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long studentId;
        private Long supervisorId;
    }
}
