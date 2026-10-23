package com.studentorgs.portal.reporting.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;
import com.studentorgs.portal.reporting.dto.EngagementRow;
import lombok.RequiredArgsConstructor;

/**
 * Read-only FR-07 queries across tables owned by other services (the one allowed exception to table
 * ownership). Standard SQL only, so the same statements run on H2 and PostgreSQL. Enum values are the
 * lowercase strings stored in the DB.
 */
@Repository
@RequiredArgsConstructor
public class ReportRepository {

    /** One parameterized query: every member with the given statuses, with two correlated counts. */
    static final String ENGAGEMENT_SQL = """
            SELECT u.id, u.display_name, u.email, u.role, u.status,
                   (SELECT COUNT(*) FROM attendance a JOIN event e ON e.id = a.event_id
                     WHERE a.user_id = u.id AND a.status = 'present'
                       AND e.starts_at >= :fromTs AND e.starts_at < :toTs) AS events_attended,
                   (SELECT COUNT(*) FROM task t
                     WHERE t.assignee_id = u.id AND t.status = 'done'
                       AND t.completed_at >= :fromTs AND t.completed_at < :toTs) AS tasks_completed
              FROM app_user u
             WHERE u.status IN (:statuses)
             ORDER BY LOWER(u.display_name), u.id
            """;

    static final String SUMMARY_SQL = """
            SELECT
              (SELECT COUNT(*) FROM app_user WHERE status = 'active') AS active_members,
              (SELECT COUNT(*) FROM membership_application WHERE decision = 'pending') AS pending_applications,
              (SELECT COUNT(*) FROM event WHERE status = 'scheduled' AND starts_at > :now) AS upcoming_events,
              (SELECT COUNT(*) FROM task WHERE status <> 'done') AS open_tasks,
              (SELECT COUNT(*) FROM task WHERE status <> 'done' AND due_date < :today) AS overdue_tasks
            """;

    public record Totals(long activeMembers, long pendingApplications, long upcomingEvents, long openTasks,
            long overdueTasks) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    /** {@code fromInclusive} ≤ time < {@code toExclusive}. */
    public List<EngagementRow> engagement(Instant fromInclusive, Instant toExclusive, Collection<UserStatus> statuses) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("fromTs", utc(fromInclusive))
                .addValue("toTs", utc(toExclusive))
                .addValue("statuses", statuses.stream().map(s -> s.name().toLowerCase(Locale.ROOT)).toList());
        return jdbc.query(ENGAGEMENT_SQL, params, (rs, i) -> new EngagementRow(
                rs.getLong("id"),
                rs.getString("display_name"),
                rs.getString("email"),
                Role.valueOf(rs.getString("role").toUpperCase(Locale.ROOT)),
                UserStatus.valueOf(rs.getString("status").toUpperCase(Locale.ROOT)),
                rs.getLong("events_attended"),
                rs.getLong("tasks_completed")));
    }

    public Totals summary(Instant now, LocalDate today) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("now", utc(now))
                .addValue("today", today);
        return jdbc.queryForObject(SUMMARY_SQL, params, (rs, i) -> new Totals(
                rs.getLong("active_members"),
                rs.getLong("pending_applications"),
                rs.getLong("upcoming_events"),
                rs.getLong("open_tasks"),
                rs.getLong("overdue_tasks")));
    }

    /** JDBC drivers (PostgreSQL in particular) bind OffsetDateTime, not Instant. */
    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
