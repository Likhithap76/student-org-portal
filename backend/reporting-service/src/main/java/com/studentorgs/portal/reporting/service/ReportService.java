package com.studentorgs.portal.reporting.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studentorgs.portal.common.config.OrgPortalProperties;
import com.studentorgs.portal.common.error.BusinessRuleException;
import com.studentorgs.portal.common.error.ServiceUnavailableException;
import com.studentorgs.portal.common.security.MemberGuard;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;
import com.studentorgs.portal.reporting.dto.EngagementReport;
import com.studentorgs.portal.reporting.dto.SummaryResponse;
import com.studentorgs.portal.reporting.repository.ReportRepository;
import com.studentorgs.portal.reporting.repository.ReportRepository.Totals;
import lombok.extern.slf4j.Slf4j;

/** FR-07: engagement report and leadership summary (OFFICER_ADMIN only, read-only). */
@Slf4j
@Service
public class ReportService {

    static final int DEFAULT_DAYS = 30;
    static final int MAX_YEARS = 2;

    private final ReportRepository reports;
    private final MemberGuard guard;
    private final Clock clock;
    private final ZoneId zone;

    public ReportService(ReportRepository reports, MemberGuard guard, Clock clock, OrgPortalProperties properties) {
        this.reports = reports;
        this.guard = guard;
        this.clock = clock;
        this.zone = properties.timeZone();
    }

    /**
     * Dates are inclusive and in the organization's time zone. Defaults: the last 30 days up to today.
     * Active members only, plus inactive ones when {@code includeInactive}; pending users never appear.
     */
    @Transactional(readOnly = true)
    public EngagementReport engagement(Long officerId, LocalDate from, LocalDate to, boolean includeInactive) {
        guard.requireRole(officerId, Role.OFFICER_ADMIN);
        Instant now = clock.instant();
        LocalDate end = to != null ? to : LocalDate.ofInstant(now, zone);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1L);
        if (start.isAfter(end)) {
            throw new BusinessRuleException("'from' must not be after 'to'");
        }
        if (start.plusYears(MAX_YEARS).isBefore(end)) {
            throw new BusinessRuleException("The report period can be at most " + MAX_YEARS + " years");
        }
        List<UserStatus> statuses = includeInactive
                ? List.of(UserStatus.ACTIVE, UserStatus.INACTIVE)
                : List.of(UserStatus.ACTIVE);
        Instant fromInclusive = start.atStartOfDay(zone).toInstant();
        Instant toExclusive = end.plusDays(1).atStartOfDay(zone).toInstant();
        return new EngagementReport(start, end, includeInactive, now,
                query(() -> reports.engagement(fromInclusive, toExclusive, statuses)));
    }

    @Transactional(readOnly = true)
    public SummaryResponse summary(Long officerId) {
        guard.requireRole(officerId, Role.OFFICER_ADMIN);
        Instant now = clock.instant();
        Totals totals = query(() -> reports.summary(now, LocalDate.ofInstant(now, zone)));
        return new SummaryResponse(totals.activeMembers(), totals.pendingApplications(), totals.upcomingEvents(),
                totals.openTasks(), totals.overdueTasks(), now);
    }

    /** Missing tables (not created yet) or an unreachable DB become 503 instead of 500. */
    private <T> T query(Supplier<T> sql) {
        try {
            return sql.get();
        } catch (BadSqlGrammarException | DataAccessResourceFailureException e) {
            log.warn("Report query failed: {}", e.getClass().getSimpleName());
            throw new ServiceUnavailableException("Report data is not available yet", e);
        }
    }
}
