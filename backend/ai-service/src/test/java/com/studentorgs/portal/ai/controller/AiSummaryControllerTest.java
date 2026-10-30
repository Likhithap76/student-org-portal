package com.studentorgs.portal.ai.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.studentorgs.portal.ai.dto.ActionItemResponse;
import com.studentorgs.portal.ai.dto.SummaryResponse;
import com.studentorgs.portal.ai.exception.LlmInvalidOutputException;
import com.studentorgs.portal.ai.exception.LlmUnavailableException;
import com.studentorgs.portal.ai.service.AiSummaryService;
import com.studentorgs.portal.common.error.BusinessRuleException;
import com.studentorgs.portal.common.security.AuthenticatedUser;
import com.studentorgs.portal.common.security.JwtService;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;

@WebMvcTest(AiSummaryController.class)
@ActiveProfiles("test")
class AiSummaryControllerTest {

    private static final String BODY = "{\"text\":\"We planned the social.\",\"committeeId\":5}";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @MockBean
    private AiSummaryService summaryService;

    private String as(long id, Role role) {
        return "Bearer " + jwtService.issue(new AuthenticatedUser(id, "u@uta.edu", "U", role, UserStatus.ACTIVE));
    }

    @Test
    void summarize_officer_returnsSummary() throws Exception {
        when(summaryService.summarize(eq(1L), any())).thenReturn(new SummaryResponse("Planned the social.",
                List.of("Friday"), List.of(new ActionItemResponse("Order pizza", "Bob", 3L, LocalDate.of(2026, 11, 1)))));

        mockMvc.perform(post("/ai/summaries").header("Authorization", as(1, Role.OFFICER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Planned the social."))
                .andExpect(jsonPath("$.actionItems[0].suggestedUserId").value(3))
                .andExpect(jsonPath("$.actionItems[0].dueDate").value("2026-11-01"));
    }

    @Test
    void summarize_member_returns403() throws Exception {
        mockMvc.perform(post("/ai/summaries").header("Authorization", as(2, Role.MEMBER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verify(summaryService, never()).summarize(anyLong(), any());
    }

    @Test
    void summarize_noToken_returns401() throws Exception {
        mockMvc.perform(post("/ai/summaries").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void summarize_missingText_returns400() throws Exception {
        mockMvc.perform(post("/ai/summaries").header("Authorization", as(1, Role.OFFICER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"committeeId\":5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void summarize_tooLong_returns422() throws Exception {
        when(summaryService.summarize(eq(1L), any())).thenThrow(new BusinessRuleException("Notes must be between 1 and 5000 characters"));

        mockMvc.perform(post("/ai/summaries").header("Authorization", as(1, Role.OFFICER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void summarize_invalidLlmOutput_returns502() throws Exception {
        when(summaryService.summarize(eq(1L), any())).thenThrow(new LlmInvalidOutputException("bad"));

        mockMvc.perform(post("/ai/summaries").header("Authorization", as(1, Role.OFFICER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void summarize_llmDown_returns503() throws Exception {
        when(summaryService.summarize(eq(1L), any())).thenThrow(new LlmUnavailableException("timeout", null));

        mockMvc.perform(post("/ai/summaries").header("Authorization", as(1, Role.OFFICER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable());
    }
}
