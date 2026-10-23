package com.studentorgs.portal.user.controller;

import static com.studentorgs.portal.user.TestUsers.NOW;
import static com.studentorgs.portal.user.TestUsers.bearer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.studentorgs.portal.common.error.BusinessRuleException;
import com.studentorgs.portal.common.error.ConflictException;
import com.studentorgs.portal.common.error.NotFoundException;
import com.studentorgs.portal.common.paging.PageResponse;
import com.studentorgs.portal.common.security.JwtService;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;
import com.studentorgs.portal.user.domain.Decision;
import com.studentorgs.portal.user.dto.ApplicationResponse;
import com.studentorgs.portal.user.service.MembershipService;

@WebMvcTest(MembershipController.class)
@ActiveProfiles("test")
class MembershipControllerTest {

    private static final ApplicationResponse PENDING =
            new ApplicationResponse(50L, 5L, "Ana", "ana@uta.edu", NOW, Decision.PENDING, null, null);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @MockBean
    private MembershipService membershipService;

    private String officer() {
        return bearer(jwtService, 1, Role.OFFICER_ADMIN, UserStatus.ACTIVE);
    }

    private String pendingMember() {
        return bearer(jwtService, 5, Role.MEMBER, UserStatus.PENDING);
    }

    @Test
    void mine_pendingUser_returnsApplication() throws Exception {
        when(membershipService.myLatest(5L)).thenReturn(PENDING);

        mockMvc.perform(get("/memberships/me").header("Authorization", pendingMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("PENDING"));
    }

    @Test
    void mine_noToken_returns401() throws Exception {
        mockMvc.perform(get("/memberships/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void mine_noApplication_returns404() throws Exception {
        when(membershipService.myLatest(5L)).thenThrow(new NotFoundException("none"));

        mockMvc.perform(get("/memberships/me").header("Authorization", pendingMember()))
                .andExpect(status().isNotFound());
    }

    @Test
    void reapply_allowed_returns201() throws Exception {
        when(membershipService.reapply(5L)).thenReturn(PENDING);

        mockMvc.perform(post("/memberships/reapply").header("Authorization", pendingMember()))
                .andExpect(status().isCreated());
    }

    @Test
    void reapply_alreadyPending_returns409() throws Exception {
        when(membershipService.reapply(5L)).thenThrow(new ConflictException("pending"));

        mockMvc.perform(post("/memberships/reapply").header("Authorization", pendingMember()))
                .andExpect(status().isConflict());
    }

    @Test
    void queue_officer_returnsPage() throws Exception {
        when(membershipService.queue(eq(1L), eq(Decision.PENDING), any(), any()))
                .thenReturn(new PageResponse<>(List.of(PENDING), 0, 20, 1, 1, true));

        mockMvc.perform(get("/memberships").param("decision", "PENDING").header("Authorization", officer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].applicantName").value("Ana"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void queue_member_returns403() throws Exception {
        mockMvc.perform(get("/memberships").header("Authorization", bearer(jwtService, 2, Role.MEMBER, UserStatus.ACTIVE)))
                .andExpect(status().isForbidden());
        verify(membershipService, never()).queue(anyLong(), any(), any(), any());
    }

    @Test
    void queue_noToken_returns401() throws Exception {
        mockMvc.perform(get("/memberships")).andExpect(status().isUnauthorized());
    }

    @Test
    void queue_badDecision_returns400() throws Exception {
        mockMvc.perform(get("/memberships").param("decision", "maybe").header("Authorization", officer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void approve_officer_returnsDecision() throws Exception {
        when(membershipService.approve(1L, 50L)).thenReturn(
                new ApplicationResponse(50L, 5L, "Ana", "ana@uta.edu", NOW, Decision.APPROVED, 1L, NOW));

        mockMvc.perform(post("/memberships/50/approve").header("Authorization", officer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("APPROVED"))
                .andExpect(jsonPath("$.decidedBy").value(1));
    }

    @Test
    void approve_notPending_returns422() throws Exception {
        when(membershipService.approve(1L, 50L)).thenThrow(new BusinessRuleException("already approved"));

        mockMvc.perform(post("/memberships/50/approve").header("Authorization", officer()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void approve_member_returns403() throws Exception {
        mockMvc.perform(post("/memberships/50/approve").header("Authorization", pendingMember()))
                .andExpect(status().isForbidden());
    }

    @Test
    void reject_officer_returnsDecision() throws Exception {
        when(membershipService.reject(1L, 50L)).thenReturn(
                new ApplicationResponse(50L, 5L, "Ana", "ana@uta.edu", NOW, Decision.REJECTED, 1L, NOW));

        mockMvc.perform(post("/memberships/50/reject").header("Authorization", officer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("REJECTED"));
    }

    @Test
    void reject_noToken_returns401() throws Exception {
        mockMvc.perform(post("/memberships/50/reject")).andExpect(status().isUnauthorized());
    }
}
