package com.studentorgs.portal.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

import com.studentorgs.portal.common.config.OrgPortalProperties;
import com.studentorgs.portal.common.error.BusinessRuleException;
import com.studentorgs.portal.common.error.ForbiddenOperationException;
import com.studentorgs.portal.common.error.ServiceUnavailableException;
import com.studentorgs.portal.common.security.MemberGuard;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;
import com.studentorgs.portal.reporting.dto.EngagementReport;
import com.studentorgs.portal.reporting.dto.EngagementRow;
import com.studentorgs.portal.reporting.dto.SummaryResponse;
import com.studentorgs.portal.reporting.repository.ReportRepository;
import com.studentorgs.portal.reporting.repository.ReportRepository.Totals;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    /** 10:00 in Chicago (CDT, UTC-5) on 2026-10-20. */
    private static final Instant NOW = Instant.parse("2026-10-20T15:00:00Z");
    private static final ZoneId CHICAGO = ZoneId.of("America/Chicago");
    private static final EngagementRow ANA =
            new EngagementRow(2L, "Ana", "ana@uta.edu", Role.MEMBER, UserStatus.ACTIVE, 3, 1);

    @Mock
    private ReportRepository reports;
    @Mock
    private MemberGuard guard;

    private ReportService service;

    @BeforeEach
    void setUp() {
        service = new ReportService(reports, guard, Clock.fixed(NOW, ZoneOffset.UTC),
                new OrgPortalProperties(null, null, null, CHICAGO));
    }

    @Test
    void engagement_explicitDates_convertsInclusiveRangeInOrgTimeZone() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        Instant expectedFrom = Instant.parse("2026-09-01T05:00:00Z");   // midnight CDT
        Instant expectedTo = Instant.parse("2026-10-01T05:00:00Z");     // midnight after Sept 30, CDT
        when(reports.engagement(expectedFrom, expectedTo, List.of(UserStatus.ACTIVE))).thenReturn(List.of(ANA));

        EngagementReport report = service.engagement(1L, from, to, false);

        assertThat(report.from()).isEqualTo(from);
        assertThat(report.to()).isEqualTo(to);
        assertThat(report.rows()).containsExactly(ANA);
        assertThat(report.generatedAt()).isEqualTo(NOW);
        verify(guard).requireRole(1L, Role.OFFICER_ADMIN);
    }

    @Test
    void engagement_noDates_defaultsToLast30DaysEndingToday() {
        when(reports.engagement(any(), any(), anyList())).thenReturn(List.of());

        EngagementReport report = service.engagement(1L, null, null, false);

        assertThat(report.to()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(report.from()).isEqualTo(LocalDate.of(2026, 9, 21));
    }

    @Test
    void engagement_onlyToGiven_fromIs29DaysBefore() {
        when(reports.engagement(any(), any(), anyList())).thenReturn(List.of());

        assertThat(service.engagement(1L, null, LocalDate.of(2026, 3, 31), false).from())
                .isEqualTo(LocalDate.of(2026, 3, 2));
    }

    @Test
    void engagement_includeInactive_queriesActiveAndInactive() {
        when(reports.engagement(any(), any(), anyList())).thenReturn(List.of());

        service.engagement(1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), true);

        verify(reports).engagement(any(), any(), org.mockito.ArgumentMatchers.eq(List.of(UserStatus.ACTIVE, UserStatus.INACTIVE)));
    }

    @Test
    void engagement_fromAfterTo_throwsBusinessRule() {
        assertThatThrownBy(() -> service.engagement(1L, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), false))
                .isInstanceOf(BusinessRuleException.class);
        verify(reports, never()).engagement(any(), any(), anyList());
    }

    @Test
    void engagement_sameDay_isAllowed() {
        LocalDate day = LocalDate.of(2026, 1, 15);
        when(reports.engagement(any(), any(), anyList())).thenReturn(List.of());

        assertThat(service.engagement(1L, day, day, false).rows()).isEmpty();
    }

    @Test
    void engagement_rangeOverTwoYears_throwsBusinessRule() {
        assertThatThrownBy(() -> service.engagement(1L, LocalDate.of(2023, 1, 1), LocalDate.of(2026, 1, 1), false))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("2 years");
    }

    @Test
    void engagement_notOfficer_throwsForbiddenWithoutQuery() {
        when(guard.requireRole(2L, Role.OFFICER_ADMIN)).thenThrow(new ForbiddenOperationException("no"));

        assertThatThrownBy(() -> service.engagement(2L, null, null, false)).isInstanceOf(ForbiddenOperationException.class);
        verify(reports, never()).engagement(any(), any(), anyList());
    }

    @Test
    void engagement_tablesMissing_throws503() {
        when(reports.engagement(any(), any(), anyList()))
                .thenThrow(new BadSqlGrammarException("report", "SELECT ...", new SQLException("Table not found")));

        assertThatThrownBy(() -> service.engagement(1L, null, null, false))
                .isInstanceOf(ServiceUnavailableException.class).hasMessage("Report data is not available yet");
    }

    @Test
    void summary_returnsTotalsWithOrgToday() {
        when(reports.summary(NOW, LocalDate.of(2026, 10, 20))).thenReturn(new Totals(12, 3, 4, 9, 2));

        SummaryResponse summary = service.summary(1L);

        assertThat(summary.activeMembers()).isEqualTo(12);
        assertThat(summary.pendingApplications()).isEqualTo(3);
        assertThat(summary.upcomingEvents()).isEqualTo(4);
        assertThat(summary.openTasks()).isEqualTo(9);
        assertThat(summary.overdueTasks()).isEqualTo(2);
        assertThat(summary.generatedAt()).isEqualTo(NOW);
    }

    @Test
    void summary_lateEveningUtc_usesOrgDateNotUtcDate() {
        // 03:00 UTC on Oct 21 is still Oct 20 in Chicago.
        Instant late = Instant.parse("2026-10-21T03:00:00Z");
        ReportService chicago = new ReportService(reports, guard, Clock.fixed(late, ZoneOffset.UTC),
                new OrgPortalProperties(null, null, null, CHICAGO));
        when(reports.summary(late, LocalDate.of(2026, 10, 20))).thenReturn(new Totals(0, 0, 0, 0, 0));

        assertThat(chicago.summary(1L).overdueTasks()).isZero();
    }

    @Test
    void summary_dbUnreachable_throws503() {
        when(reports.summary(any(), any())).thenThrow(new CannotGetJdbcConnectionException("down"));

        assertThatThrownBy(() -> service.summary(1L)).isInstanceOf(ServiceUnavailableException.class);
    }
}
