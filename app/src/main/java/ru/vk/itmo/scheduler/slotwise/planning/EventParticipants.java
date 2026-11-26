package ru.vk.itmo.scheduler.slotwise.planning;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
class EventParticipants {
    private final JdbcClient jdbc;

    record Group(long id, String name) {
    }

    record Target(Long group, String label, List<Long> students) {
    }

    record Link(long source, long person) {
    }

    List<Group> groups(Set<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("select id,name from slotwise.study_group where active and id in (:ids) order by id")
                .param("ids", ids).query((rs, row) -> new Group(rs.getLong(1), rs.getString(2))).list();
    }

    Map<Long, List<Long>> groupStudents(Set<Long> groups) {
        if (groups.isEmpty()) {
            return Map.of();
        }
        return links(jdbc.sql("""
                select m.study_group_id,m.account_id from slotwise.study_group_member m
                join slotwise.account a on a.id=m.account_id
                where m.study_group_id in (:ids) and a.enabled and a.role='STUDENT'
                order by m.study_group_id,m.account_id
                """).param("ids", groups));
    }

    Map<Long, List<Long>> studentGroups(Set<Long> students) {
        if (students.isEmpty()) {
            return Map.of();
        }
        return links(jdbc.sql("""
                select m.account_id,m.study_group_id from slotwise.study_group_member m
                join slotwise.study_group g on g.id=m.study_group_id and g.active
                where m.account_id in (:ids) order by m.account_id,m.study_group_id
                """).param("ids", students));
    }

    Map<Long, String> studentNames(Set<Long> students) {
        if (students.isEmpty()) {
            return Map.of();
        }
        return jdbc.sql("""
                select id,concat_ws(' ',last_name,first_name,patronymic) from slotwise.account
                where enabled and role='STUDENT' and id in (:ids)
                """).param("ids", students).query((rs, row) -> Map.entry(rs.getLong(1), rs.getString(2))).list()
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    Map<Long, List<Long>> teachers(Long discipline, Set<Long> groups) {
        if (discipline == null || groups.isEmpty()) {
            return Map.of();
        }
        return links(jdbc.sql("""
                select t.study_group_id,t.teacher_id from slotwise.teaching t
                join slotwise.account a on a.id=t.teacher_id and a.enabled and a.role='TEACHER'
                where t.discipline_id=:discipline and t.study_group_id in (:groups)
                order by t.study_group_id,t.teacher_id
                """).param("discipline", discipline).param("groups", groups));
    }

    Map<Long, List<Long>> supervisors(Collection<Long> students) {
        if (students.isEmpty()) {
            return Map.of();
        }
        return links(jdbc.sql("""
                select s.student_id,s.supervisor_id from slotwise.supervision s
                join slotwise.account a on a.id=s.supervisor_id and a.enabled and a.role='TEACHER'
                where s.student_id in (:students) order by s.student_id,s.supervisor_id
                """).param("students", students));
    }

    Map<Long, String> activeRoles(Collection<Long> accounts) {
        if (accounts.isEmpty()) {
            return Map.of();
        }
        return jdbc.sql("select id,role from slotwise.account where enabled and id in (:accounts)")
                .param("accounts", accounts).query((rs, row) -> Map.entry(rs.getLong(1), rs.getString(2))).list()
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Map<Long, List<Long>> links(JdbcClient.StatementSpec query) {
        return query.query((rs, row) -> new Link(rs.getLong(1), rs.getLong(2))).list().stream()
                .collect(Collectors.groupingBy(Link::source, Collectors.mapping(Link::person, Collectors.toList())));
    }
}
