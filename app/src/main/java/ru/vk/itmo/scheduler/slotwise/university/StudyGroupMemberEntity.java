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
@Table(name = "study_group_member")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@IdClass(StudyGroupMemberEntity.Key.class)
public class StudyGroupMemberEntity {

    @Id
    private Long studyGroupId;

    @Id
    private Long accountId;

    @Data
    public static class Key implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long studyGroupId;
        private Long accountId;
    }
}
