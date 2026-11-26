package ru.vk.itmo.scheduler.slotwise.planning;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;
import ru.vk.itmo.scheduler.slotwise.common.api.PageResponse;
import ru.vk.itmo.scheduler.slotwise.planning.api.dto.PlanningSessionResponse;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
class SessionPreparationStore {
    private final JdbcClient jdbc;
    private final Clock clock;

    record Revision(long version, long inputVersion) {
    }

    record Settings(String name, Instant startsAt, Instant endsAt, String timeZone, Set<DayOfWeek> days,
                    LocalTime workingStart, LocalTime workingEnd, Instant collectionStart, Instant collectionEnd) {
    }

    private static final String SELECT = """
            select s.*, array(select day_of_week::integer from slotwise.session_working_day
                              where planning_session_id=s.id) as working_days
            from slotwise.planning_session s
            """;

    private static final String VISIBLE = """
             where (s.organizer_id=:user or (:participant and exists (
               select from slotwise.session_participant p where p.planning_session_id=s.id and p.account_id=:user)))
            """;

    PageResponse<PlanningSessionResponse> list(long user, boolean participant, int page, int size) {
        long total = jdbc.sql("select count(*) from slotwise.planning_session s" + VISIBLE)
                .param("user", user).param("participant", participant).query(Long.class).single();
        var items = jdbc.sql(SELECT + VISIBLE + " order by s.id desc limit :size offset :offset")
                .param("user", user).param("participant", participant).param("size", size).param("offset", (long) page * size)
                .query(this::map).list();
        return new PageResponse<>(items, total, page, size);
    }

    PlanningSessionResponse visible(long id, long user, boolean participant) {
        return jdbc.sql(SELECT + VISIBLE + " and s.id=:id")
                .param("user", user).param("participant", participant).param("id", id)
                .query(this::map).optional().orElseThrow(SessionPreparationStore::missing);
    }

    PlanningSessionResponse get(long id) {
        return jdbc.sql(SELECT + " where s.id=:id").param("id", id).query(this::map).single();
    }

    Revision owned(long id, long owner, boolean write) {
        return jdbc.sql("""
                select organizer_id,version,input_version from slotwise.planning_session where id=:id
                """ + (write ? " for update" : " for share"))
                .param("id", id).query((rs, row) -> {
                    if (rs.getLong("organizer_id") != owner) {
                        throw new AccessDeniedException("Не владелец сессии");
                    }
                    return new Revision(rs.getLong("version"), rs.getLong("input_version"));
                }).optional().orElseThrow(SessionPreparationStore::missing);
    }

    long create(long owner, Settings data) {
        long id = parameters(jdbc.sql("""
                insert into slotwise.planning_session
                    (organizer_id,name,starts_at,ends_at,time_zone,working_day_start,working_day_end,
                     collection_starts_at,collection_ends_at)
                values (:owner,:name,:starts,:ends,:zone,:workingStart,:workingEnd,:collectionStart,:collectionEnd)
                on conflict (organizer_id,name) do nothing returning id
                """), data).param("owner", owner).query(Long.class).optional().orElseThrow(SessionPreparationStore::duplicate);
        replaceDays(id, data.days());
        return id;
    }

    void update(long id, Settings data) {
        parameters(jdbc.sql("""
                update slotwise.planning_session set name=:name,starts_at=:starts,ends_at=:ends,time_zone=:zone,
                    working_day_start=:workingStart,working_day_end=:workingEnd,collection_starts_at=:collectionStart,
                    collection_ends_at=:collectionEnd,version=version+1,input_version=input_version+1
                where id=:id
                """), data).param("id", id).update();
        replaceDays(id, data.days());
    }

    Revision bump(long id) {
        return jdbc.sql("""
                update slotwise.planning_session set version=version+1,input_version=input_version+1
                where id=:id returning version,input_version
                """).param("id", id).query((rs, row) -> new Revision(rs.getLong(1), rs.getLong(2))).single();
    }

    private static JdbcClient.StatementSpec parameters(JdbcClient.StatementSpec sql, Settings settings) {
        return sql.param("name", settings.name()).param("starts", utc(settings.startsAt()))
                .param("ends", utc(settings.endsAt())).param("zone", settings.timeZone())
                .param("workingStart", settings.workingStart()).param("workingEnd", settings.workingEnd())
                .param("collectionStart", utc(settings.collectionStart())).param("collectionEnd", utc(settings.collectionEnd()));
    }

    private void replaceDays(long id, Set<DayOfWeek> days) {
        jdbc.sql("delete from slotwise.session_working_day where planning_session_id=:id").param("id", id).update();
        for (var day : days) {
            jdbc.sql("insert into slotwise.session_working_day values (:id,:day)")
                    .param("id", id).param("day", day.getValue()).update();
        }
    }

    private PlanningSessionResponse map(ResultSet rs, int row) throws SQLException {
        var days = Arrays.stream((Integer[]) rs.getArray("working_days").getArray())
                .map(day -> DayOfWeek.of(day)).collect(Collectors.toSet());
        var collectionStart = instant(rs, "collection_starts_at");
        var collectionEnd = instant(rs, "collection_ends_at");
        var status = CollectionStatus.valueOf(rs.getString("collection_status"))
                .effectiveAt(clock.instant(), collectionStart, collectionEnd);
        return new PlanningSessionResponse(rs.getLong("id"), rs.getLong("organizer_id"), rs.getString("name"),
                instant(rs, "starts_at"), instant(rs, "ends_at"), rs.getString("time_zone"), days,
                rs.getObject("working_day_start", LocalTime.class), rs.getObject("working_day_end", LocalTime.class),
                collectionStart, collectionEnd, status, rs.getLong("input_version"), rs.getLong("version"),
                instant(rs, "created_at"));
    }

    static PlanningException duplicate() {
        return new PlanningException(HttpStatus.CONFLICT, "SESSION_NAME_CONFLICT", "У организатора уже есть сессия с этим названием");
    }

    private static PlanningException missing() {
        return new PlanningException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Сессия не найдена");
    }

    private static Instant instant(ResultSet rs, String name) throws SQLException {
        return rs.getObject(name, OffsetDateTime.class).toInstant();
    }

    private static OffsetDateTime utc(Instant value) {
        return value.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }
}
