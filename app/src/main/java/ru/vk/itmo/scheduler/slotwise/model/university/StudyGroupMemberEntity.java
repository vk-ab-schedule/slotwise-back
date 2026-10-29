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

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "study_group_member", schema = "slotwise")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(StudyGroupMemberEntity.Key.class)
public class StudyGroupMemberEntity {

    @Id
    @Column(name = "study_group_id")
    private Long studyGroupId;

    @Id
    @Column(name = "account_id")
    private Long accountId;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Key implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long studyGroupId;
        private Long accountId;

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Key that)) return false;
            return Objects.equals(studyGroupId, that.studyGroupId) && Objects.equals(accountId, that.accountId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(studyGroupId, accountId);
        }
    }
}
