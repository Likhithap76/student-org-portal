package com.studentorgs.portal.task.controller;

import static com.studentorgs.portal.task.TestTasks.NOW;
import static com.studentorgs.portal.task.TestTasks.bearer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.studentorgs.portal.common.error.BusinessRuleException;
import com.studentorgs.portal.common.error.ConflictException;
import com.studentorgs.portal.common.paging.PageResponse;
import com.studentorgs.portal.common.security.JwtService;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.task.domain.CommitteeKind;
import com.studentorgs.portal.task.dto.CommitteeDetailResponse;
import com.studentorgs.portal.task.dto.CommitteeMemberResponse;
import com.studentorgs.portal.task.dto.CommitteeSummaryResponse;
import com.studentorgs.portal.task.service.CommitteeService;

@WebMvcTest(CommitteeController.class)
@ActiveProfiles("test")
class CommitteeControllerTest {

    private static final CommitteeSummaryResponse SUMMARY =
            new CommitteeSummaryResponse(5L, "Events", CommitteeKind.COMMITTEE, null, 2);
    private static final CommitteeDetailResponse DETAIL = new CommitteeDetailResponse(5L, "Events",
            CommitteeKind.COMMITTEE, null, List.of(new CommitteeMemberResponse(2L, "Ana", NOW)));

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @MockBean
    private CommitteeService committeeService;

    private String officer() {
        return bearer(jwtService, 1, Role.OFFICER_ADMIN);
    }

    private String member() {
        return bearer(jwtService, 2, Role.MEMBER);
    }

    @Test
    void create_officer_returns201() throws Exception {
        when(committeeService.create(eq(1L), any())).thenReturn(SUMMARY);

        mockMvc.perform(post("/committees").header("Authorization", officer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Events\",\"kind\":\"COMMITTEE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("COMMITTEE"));
    }

    @Test
    void create_missingKind_returns400() throws Exception {
        mockMvc.perform(post("/committees").header("Authorization", officer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Events\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("kind"));
    }

    @Test
    void create_duplicate_returns409() throws Exception {
        when(committeeService.create(eq(1L), any())).thenThrow(new ConflictException("exists"));

        mockMvc.perform(post("/committees").header("Authorization", officer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Events\",\"kind\":\"PROJECT\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void create_member_returns403() throws Exception {
        mockMvc.perform(post("/committees").header("Authorization", member()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Events\",\"kind\":\"PROJECT\"}"))
                .andExpect(status().isForbidden());
        verify(committeeService, never()).create(anyLong(), any());
    }

    @Test
    void list_noToken_returns401() throws Exception {
        mockMvc.perform(get("/committees")).andExpect(status().isUnauthorized());
    }

    @Test
    void list_member_returnsPage() throws Exception {
        when(committeeService.list(2L, null, null)).thenReturn(new PageResponse<>(List.of(SUMMARY), 0, 20, 1, 1, true));

        mockMvc.perform(get("/committees").header("Authorization", member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].memberCount").value(2));
    }

    @Test
    void get_member_returnsDetailWithMembers() throws Exception {
        when(committeeService.get(2L, 5L)).thenReturn(DETAIL);

        mockMvc.perform(get("/committees/5").header("Authorization", member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].displayName").value("Ana"));
    }

    @Test
    void addMembers_officer_returnsDetail() throws Exception {
        when(committeeService.addMembers(eq(1L), eq(5L), any())).thenReturn(DETAIL);

        mockMvc.perform(post("/committees/5/members").header("Authorization", officer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userIds\":[2]}"))
                .andExpect(status().isOk());
    }

    @Test
    void addMembers_emptyList_returns400() throws Exception {
        mockMvc.perform(post("/committees/5/members").header("Authorization", officer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userIds\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addMembers_member_returns403() throws Exception {
        mockMvc.perform(post("/committees/5/members").header("Authorization", member())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userIds\":[2]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void removeMember_officer_returns204() throws Exception {
        mockMvc.perform(delete("/committees/5/members/2").header("Authorization", officer()))
                .andExpect(status().isNoContent());
        verify(committeeService).removeMember(1L, 5L, 2L);
    }

    @Test
    void removeMember_openTasks_returns422() throws Exception {
        doThrow(new BusinessRuleException("open tasks")).when(committeeService).removeMember(1L, 5L, 2L);

        mockMvc.perform(delete("/committees/5/members/2").header("Authorization", officer()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void removeMember_noToken_returns401() throws Exception {
        mockMvc.perform(delete("/committees/5/members/2")).andExpect(status().isUnauthorized());
    }
}
