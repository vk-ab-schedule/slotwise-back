package ru.vk.itmo.scheduler.slotwise.planning;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ru.vk.itmo.scheduler.slotwise.account.AccountRole;
import ru.vk.itmo.scheduler.slotwise.common.api.PersonSummary;
import ru.vk.itmo.scheduler.slotwise.planning.SessionPreparationStore.Revision;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.SessionSelection;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static ru.vk.itmo.scheduler.slotwise.planning.SessionPreparationService.conflict;
import static ru.vk.itmo.scheduler.slotwise.planning.SessionPreparationService.invalid;

@Repository
@RequiredArgsConstructor
class SessionMembership {
    private final JdbcClient jdbc;

    private static final String SELECTED_PEOPLE = """
            select account_id from slotwise.session_selected_account where planning_session_id=:id
            union
            select m.account_id from slotwise.session_selected_group g
            join slotwise.study_group_member m on m.study_group_id=g.study_group_id
            join slotwise.account a on a.id=m.account_id and a.enabled and a.role='STUDENT'
            where g.planning_session_id=:id
            """;

    private static final String REQUIRED_PEOPLE = SELECTED_PEOPLE + """
            union select account_id from slotwise.event_participant where planning_session_id=:id
            union select account_id from slotwise.commission_candidate where planning_session_id=:id
            """;

    SessionSelection.ParticipantsResponse participants(long id, Revision revision) {
        Set<Long> groups = Set.copyOf(jdbc.sql("""
                select study_group_id from slotwise.session_selected_group where planning_session_id=:id
                """).param("id", id).query(Long.class).list());
        Set<Long> accounts = Set.copyOf(jdbc.sql("""
                select account_id from slotwise.session_selected_account where planning_session_id=:id
                """).param("id", id).query(Long.class).list());
        var resolved = jdbc.sql("""
                select a.id,a.last_name,a.first_name,a.patronymic,a.role from slotwise.session_participant p
                join slotwise.account a on a.id=p.account_id where p.planning_session_id=:id order by a.id
                """).param("id", id).query((rs, row) -> new PersonSummary(rs.getLong(1), rs.getString(2),
                        rs.getString(3), rs.getString(4), AccountRole.valueOf(rs.getString(5)))).list();
        return new SessionSelection.ParticipantsResponse(id, revision.version(), revision.inputVersion(), groups, accounts, resolved);
    }

    void replaceParticipants(long id, Set<Long> groups, Set<Long> accounts) {
        requireCatalogIds(groups, """
                select count(*) from slotwise.study_group where active and id in (:ids)
                """, "INVALID_STUDY_GROUP", "Выберите активные учебные группы");
        requireCatalogIds(accounts, """
                select count(*) from slotwise.account where enabled and role in ('STUDENT','TEACHER') and id in (:ids)
                """, "INVALID_PARTICIPANT", "Выберите активных студентов или преподавателей");

        var removed = new HashSet<>(jdbc.sql(SELECTED_PEOPLE).param("id", id).query(Long.class).list());
        removed.removeAll(accounts);
        if (!groups.isEmpty()) {
            removed.removeAll(jdbc.sql("""
                    select m.account_id from slotwise.study_group_member m join slotwise.account a on a.id=m.account_id
                    where m.study_group_id in (:groups) and a.enabled and a.role='STUDENT'
                    """).param("groups", groups).query(Long.class).list());
        }
        if (!removed.isEmpty() && jdbc.sql("""
                select exists(select from slotwise.event_participant where planning_session_id=:id and account_id in (:removed))
                """).param("id", id).param("removed", removed).query(Boolean.class).single()) {
            throw conflict("PARTICIPANT_USED_BY_EVENT", "Нельзя убрать участника, используемого в событии");
        }
        jdbc.sql("delete from slotwise.session_selected_group where planning_session_id=:id").param("id", id).update();
        jdbc.sql("delete from slotwise.session_selected_account where planning_session_id=:id").param("id", id).update();
        for (long group : groups) {
            jdbc.sql("insert into slotwise.session_selected_group values (:id,:group)")
                    .param("id", id).param("group", group).update();
        }
        for (long account : accounts) {
            jdbc.sql("insert into slotwise.session_selected_account values (:id,:account)")
                    .param("id", id).param("account", account).update();
        }
        reconcile(id);
    }

    SessionSelection.CommissionPoolResponse commission(long id, Revision revision) {
        var candidates = jdbc.sql("""
                select account_id,can_chair from slotwise.commission_candidate where planning_session_id=:id order by account_id
                """).param("id", id).query((rs, row) -> new SessionSelection.CommissionCandidate(rs.getLong(1),
                        rs.getBoolean(2))).list();
        return new SessionSelection.CommissionPoolResponse(id, revision.version(), revision.inputVersion(), candidates);
    }

    void replaceCommission(long id, List<SessionSelection.CommissionCandidate> candidates) {
        var accounts = new HashSet<Long>();
        for (var candidate : candidates) {
            if (!accounts.add(candidate.accountId())) {
                throw invalid("DUPLICATE_COMMISSION_CANDIDATE", "Преподаватель указан в пуле дважды");
            }
        }
        requireCatalogIds(accounts, """
                select count(*) from slotwise.account where enabled and role='TEACHER' and id in (:ids)
                """, "INVALID_COMMISSION_CANDIDATE", "В пул комиссии можно добавить только активных преподавателей");
        var removed = new HashSet<>(jdbc.sql("""
                select account_id from slotwise.commission_candidate where planning_session_id=:id
                """).param("id", id).query(Long.class).list());
        removed.removeAll(accounts);
        if (!removed.isEmpty()) {
            if (jdbc.sql("""
                    select exists(select from slotwise.assignment_commission_member
                        where planning_session_id=:id and account_id in (:removed))
                    """).param("id", id).param("removed", removed).query(Boolean.class).single()) {
                throw conflict("COMMISSION_USED_BY_VARIANT", "Участник комиссии используется в сохранённом варианте");
            }
            jdbc.sql("delete from slotwise.commission_candidate where planning_session_id=:id and account_id in (:removed)")
                    .param("id", id).param("removed", removed).update();
        }
        add(id, accounts);
        for (var candidate : candidates) {
            jdbc.sql("""
                    insert into slotwise.commission_candidate (planning_session_id,account_id,can_chair)
                    values (:id,:account,:chair) on conflict (planning_session_id,account_id)
                    do update set can_chair=excluded.can_chair
                    """).param("id", id).param("account", candidate.accountId()).param("chair", candidate.canChair()).update();
        }
        reconcile(id);
    }

    SessionSelection.RoomsResponse rooms(long id, Revision revision) {
        return new SessionSelection.RoomsResponse(id, revision.version(), revision.inputVersion(), roomIds(id));
    }

    void replaceRooms(long id, Set<Long> rooms) {
        requireCatalogIds(rooms, "select count(*) from slotwise.room where active and id in (:ids)",
                "INVALID_ROOM", "Выберите активные аудитории или онлайн-площадки");
        var existing = roomIds(id);
        var removed = new HashSet<>(existing);
        removed.removeAll(rooms);
        if (!removed.isEmpty()) {
            if (jdbc.sql("""
                    select exists(select from slotwise.assignment where planning_session_id=:id and room_id in (:removed))
                    """).param("id", id).param("removed", removed).query(Boolean.class).single()) {
                throw conflict("ROOM_USED_BY_VARIANT", "Аудитория используется в сохранённом варианте");
            }
            jdbc.sql("delete from slotwise.session_room where planning_session_id=:id and room_id in (:removed)")
                    .param("id", id).param("removed", removed).update();
        }
        for (long room : rooms) {
            if (!existing.contains(room)) {
                jdbc.sql("insert into slotwise.session_room values (:id,:room)")
                        .param("id", id).param("room", room).update();
            }
        }
    }

    private Set<Long> roomIds(long id) {
        return Set.copyOf(jdbc.sql("select room_id from slotwise.session_room where planning_session_id=:id")
                .param("id", id).query(Long.class).list());
    }

    void add(long id, Collection<Long> accounts) {
        if (!accounts.isEmpty()) {
            jdbc.sql("""
                    insert into slotwise.session_participant (planning_session_id,account_id)
                    select :id,id from slotwise.account where id in (:accounts)
                    on conflict do nothing
                    """).param("id", id).param("accounts", accounts).update();
        }
    }

    void reconcile(long id) {
        var required = jdbc.sql(REQUIRED_PEOPLE).param("id", id).query(Long.class).list();
        add(id, required);
        var removed = new HashSet<>(jdbc.sql("""
                select account_id from slotwise.session_participant where planning_session_id=:id
                """).param("id", id).query(Long.class).list());
        removed.removeAll(required);
        if (removed.isEmpty()) {
            return;
        }
        if (jdbc.sql("""
                select exists(select from slotwise.change_request where planning_session_id=:id and requester_id in (:removed))
                """).param("id", id).param("removed", removed).query(Boolean.class).single()) {
            throw conflict("PARTICIPANT_USED_BY_REQUEST", "Участник связан с запросом на перенос");
        }
        jdbc.sql("delete from slotwise.busy_interval where planning_session_id=:id and account_id in (:removed)")
                .param("id", id).param("removed", removed).update();
        jdbc.sql("delete from slotwise.session_participant where planning_session_id=:id and account_id in (:removed)")
                .param("id", id).param("removed", removed).update();
    }

    void requireCatalogIds(Collection<Long> ids, String sql, String code, String message) {
        if (!ids.isEmpty() && jdbc.sql(sql).param("ids", ids).query(Long.class).single() != ids.size()) {
            throw invalid(code, message);
        }
    }
}
