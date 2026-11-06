package ru.vk.itmo.scheduler.slotwise.university;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;

@Entity
@Getter
@Setter
@DynamicInsert
@Table(name = "teaching")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeachingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long teacherId;
    private Long disciplineId;
    private Long studyGroupId;
}
