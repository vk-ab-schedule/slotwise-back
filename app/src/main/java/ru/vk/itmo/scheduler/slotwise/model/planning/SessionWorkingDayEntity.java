package ru.vk.itmo.scheduler.slotwise.model.planning;

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

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

@Entity
@DynamicInsert
@Table(name = "session_working_day", schema = "slotwise")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(SessionWorkingDayEntity.Key.class)
public class SessionWorkingDayEntity {

    @Id
    @Column(name = "planning_session_id")
    private Long planningSessionId;

    @Id
    @Column(name = "day_of_week")
    private Short dayOfWeek;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Key implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long planningSessionId;
        private Short dayOfWeek;

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key that)) {
                return false;
            }
            return Objects.equals(planningSessionId, that.planningSessionId) && Objects.equals(dayOfWeek, that.dayOfWeek);
        }

        @Override
        public int hashCode() {
            return Objects.hash(planningSessionId, dayOfWeek);
        }
    }
}
