package ru.vk.itmo.scheduler.slotwise.model.university;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "supervision", schema = "slotwise")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(SupervisionEntity.Key.class)
public class SupervisionEntity {

    @Id
    @Column(name = "student_id")
    private Long studentId;

    @Id
    @Column(name = "supervisor_id")
    private Long supervisorId;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Key implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long studentId;
        private Long supervisorId;

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key that)) return false;
            return Objects.equals(studentId, that.studentId) && Objects.equals(supervisorId, that.supervisorId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(studentId, supervisorId);
        }
    }
}
