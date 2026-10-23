package com.studentorgs.portal.reporting.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.studentorgs.portal.common.error.BusinessRuleException;
import com.studentorgs.portal.common.error.ServiceUnavailableException;
import com.studentorgs.portal.common.security.AuthenticatedUser;
import com.studentorgs.portal.common.security.JwtService;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;
import com.studentorgs.portal.reporting.dto.EngagementReport;
import com.studentorgs.portal.reporting.dto.EngagementRow;
import com.studentorgs.portal.reporting.dto.SummaryResponse;
import com.studentorgs.portal.reporting.service.ReportService;

@WebMvcTest(ReportController.class)
@ActiveProfiles("test")
class ReportControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-20T15:00:00Z");
    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);
    private static final EngagementReport REPORT = new EngagementReport(FROM, TO, false, NOW,
            List.of(new EngagementRow(2L, "Ana", "ana@uta.edu", Role.MEMBER, UserStatus.ACTIVE, 3, 1)));

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @MockBean
    private ReportService reportService;

    private String as(long id, Role role) {
        return "Bearer " + jwtService.issue(new AuthenticatedUser(id, "u@uta.edu", "U", role, UserStatus.ACTIVE));
    }

    @Test
    void engagement_officer_returnsJson() throws Exception {
        when(reportService.engagement(1L, FROM, TO, false)).thenReturn(REPORT);

        mockMvc.perform(get("/reports/engagement").param("from", "2026-09-01").param("to", "2026-09-30")
                        .header("Authorization", as(1, Role.OFFICER_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-09-01"))
                .andExpect(jsonPath("$.rows[0].eventsAttended").value(3))
                .andExpect(jsonPath("$.rows[0].tasksCompleted").value(1));
    }

    @Test
    void engagement_includeInactive_passedThrough() throws Exception {
        when(reportService.engagement(9L, null, null, true)).thenReturn(REPORT);

        mockMvc.perform(get("/reports/engagement").param("includeInactive", "true").header("Authorization", as(9, Role.OFFICER_ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void engagement_member_returns403() throws Exception {
        mockMvc.perform(get("/reports/engagement").header("Authorization", as(2, Role.MEMBER)))
                .andExpect(status().isForbidden());
        verify(reportService, never()).engagement(anyLong(), any(), any(), anyBoolean());
    }

    @Test
    void engagement_noToken_returns401() throws Exception {
        mockMvc.perform(get("/reports/engagement")).andExpect(status().isUnauthorized());
    }

    @Test
    void engagement_badDate_returns400() throws Exception {
        mockMvc.perform(get("/reports/engagement").param("from", "09/01/2026").header("Authorization", as(1, Role.OFFICER_ADMIN)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void engagement_fromAfterTo_returns422() throws Exception {
        when(reportService.engagement(1L, TO, FROM, false)).thenThrow(new BusinessRuleException("'from' must not be after 'to'"));

        mockMvc.perform(get("/reports/engagement").param("from", "2026-09-30").param("to", "2026-09-01")
                        .header("Authorization", as(1, Role.OFFICER_ADMIN)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void engagementCsv_officer_returnsAttachment() throws Exception {
        when(reportService.engagement(1L, FROM, TO, false)).thenReturn(REPORT);

        mockMvc.perform(get("/reports/engagement.csv").param("from", "2026-09-01").param("to", "2026-09-30")
                        .header("Authorization", as(1, Role.OFFICER_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"engagement-2026-09-01-2026-09-30.csv\""))
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("user_id,display_name")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2,Ana,ana@uta.edu,member,active,3,1")));
    }

    @Test
    void engagementCsv_member_returns403() throws Exception {
        mockMvc.perform(get("/reports/engagement.csv").header("Authorization", as(2, Role.MEMBER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void summary_officer_returnsTotals() throws Exception {
        when(reportService.summary(1L)).thenReturn(new SummaryResponse(12, 3, 4, 9, 2, NOW));

        mockMvc.perform(get("/reports/summary").header("Authorization", as(1, Role.OFFICER_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeMembers").value(12))
                .andExpect(jsonPath("$.overdueTasks").value(2));
    }

    @Test
    void summary_dataNotReady_returns503() throws Exception {
        when(reportService.summary(1L)).thenThrow(new ServiceUnavailableException("Report data is not available yet", null));

        mockMvc.perform(get("/reports/summary").header("Authorization", as(1, Role.OFFICER_ADMIN)))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void summary_noToken_returns401() throws Exception {
        mockMvc.perform(get("/reports/summary")).andExpect(status().isUnauthorized());
    }
}
