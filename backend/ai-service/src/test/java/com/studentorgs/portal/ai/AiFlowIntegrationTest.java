package com.studentorgs.portal.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.studentorgs.portal.ai.client.LlmClient;
import com.studentorgs.portal.ai.client.MeetingSummary;
import com.studentorgs.portal.ai.client.MeetingSummary.ActionItem;
import com.studentorgs.portal.ai.client.TaskDirectoryClient;
import com.studentorgs.portal.ai.client.TaskDirectoryClient.CommitteeMember;
import com.studentorgs.portal.ai.domain.AiOutcome;
import com.studentorgs.portal.ai.repository.AiRequestLogRepository;
import com.studentorgs.portal.common.client.UserDirectoryClient;
import com.studentorgs.portal.common.client.UserDirectoryClient.UserStatusInfo;
import com.studentorgs.portal.common.security.AuthenticatedUser;
import com.studentorgs.portal.common.security.JwtService;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;

/**
 * Summary end to end on in-memory H2. The LLM answer is stubbed on the HttpLlmClient template;
 * user- and task-service are mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtService jwtService;
    @SpyBean
    private LlmClient llmClient;
    @Autowired
    private AiRequestLogRepository logs;
    @MockBean
    private UserDirectoryClient users;
    @MockBean
    private TaskDirectoryClient taskDirectory;

    @BeforeEach
    void setUp() {
        when(users.status(1L)).thenReturn(Optional.of(new UserStatusInfo(UserStatus.ACTIVE, Role.OFFICER_ADMIN)));
        when(taskDirectory.committeeMembers(5L)).thenReturn(List.of(
                new CommitteeMember(2L, "Ana Lopez"), new CommitteeMember(3L, "Bob Lee")));
    }

    @Test
    void summarize_providerNotConfigured_returns503AndLogsServiceError() throws Exception {
        long before = logs.count();

        summarize("Budget review").andExpect(status().isServiceUnavailable());

        assertThat(logs.count()).isEqualTo(before + 1);
        assertThat(logs.findAll().get((int) before).getOutcome()).isEqualTo(AiOutcome.SERVICE_ERROR);
    }

    @Test
    void summarize_withCommittee_suggestsMembersAndLogsWithoutText() throws Exception {
        String notes = "Budget review and spring social planning.";
        doReturn(new MeetingSummary("Budget review.", List.of("Social on Friday"), List.of(
                new ActionItem("Book ERB 101", "Ana Lopez", LocalDate.parse("2026-11-01")),
                new ActionItem("Order pizza", "Bob", null),
                new ActionItem("Buy cups", "Zed", null)))).when(llmClient).summarize(notes);
        long before = logs.count();

        summarize(notes)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisions[0]").value("Social on Friday"))
                .andExpect(jsonPath("$.actionItems.length()").value(3))
                .andExpect(jsonPath("$.actionItems[0].suggestedUserId").value(2))
                .andExpect(jsonPath("$.actionItems[0].dueDate").value("2026-11-01"))
                .andExpect(jsonPath("$.actionItems[1].suggestedUserId").value(3))
                .andExpect(jsonPath("$.actionItems[2].suggestedUserId").doesNotExist());

        assertThat(logs.count()).isEqualTo(before + 1);
        var row = logs.findAll().get((int) before);
        assertThat(row.getOutcome()).isEqualTo(AiOutcome.SUCCEEDED);
        assertThat(row.getInputChars()).isEqualTo(notes.length());
    }

    private ResultActions summarize(String notes) throws Exception {
        return mockMvc.perform(post("/ai/summaries").header("Authorization",
                        "Bearer " + jwtService.issue(new AuthenticatedUser(1L, "o@uta.edu", "O", Role.OFFICER_ADMIN, UserStatus.ACTIVE)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("text", notes, "committeeId", 5))));
    }
}
