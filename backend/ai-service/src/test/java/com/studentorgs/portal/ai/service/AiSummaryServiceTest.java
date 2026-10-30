package com.studentorgs.portal.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.studentorgs.portal.ai.client.LlmClient;
import com.studentorgs.portal.ai.client.MeetingSummary;
import com.studentorgs.portal.ai.client.TaskDirectoryClient;
import com.studentorgs.portal.ai.client.TaskDirectoryClient.CommitteeMember;
import com.studentorgs.portal.ai.domain.AiOutcome;
import com.studentorgs.portal.ai.domain.AiRequestLog;
import com.studentorgs.portal.ai.dto.SummaryRequest;
import com.studentorgs.portal.ai.dto.SummaryResponse;
import com.studentorgs.portal.ai.exception.LlmInvalidOutputException;
import com.studentorgs.portal.ai.exception.LlmUnavailableException;
import com.studentorgs.portal.ai.repository.AiRequestLogRepository;
import com.studentorgs.portal.common.error.BusinessRuleException;
import com.studentorgs.portal.common.error.ForbiddenOperationException;
import com.studentorgs.portal.common.security.MemberGuard;
import com.studentorgs.portal.common.security.Role;

@ExtendWith(MockitoExtension.class)
class AiSummaryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T15:00:00Z");
    private static final String NOTES = "We planned the social.\nAction: Bob - Order pizza";
    private static final MeetingSummary RESULT = new MeetingSummary("We planned the social.", List.of("Friday"),
            List.of(new MeetingSummary.ActionItem("Order pizza", "Bob", LocalDate.of(2026, 11, 1)),
                    new MeetingSummary.ActionItem("Call venue", "Ana", null)));

    @Mock
    private LlmClient llm;
    @Mock
    private TaskDirectoryClient taskDirectory;
    @Mock
    private AiRequestLogRepository logs;
    @Mock
    private MemberGuard guard;

    private AiSummaryService service;

    @BeforeEach
    void setUp() {
        service = new AiSummaryService(llm, taskDirectory, logs, guard, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private AiRequestLog savedLog() {
        ArgumentCaptor<AiRequestLog> saved = ArgumentCaptor.forClass(AiRequestLog.class);
        verify(logs).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void summarize_withCommittee_matchesAssigneesAndLogsSuccess() {
        when(taskDirectory.committeeMembers(5L)).thenReturn(List.of(new CommitteeMember(3L, "Bob Lee")));
        when(llm.summarize(NOTES)).thenReturn(RESULT);

        SummaryResponse response = service.summarize(1L, new SummaryRequest(NOTES, 5L));

        assertThat(response.summary()).isEqualTo("We planned the social.");
        assertThat(response.decisions()).containsExactly("Friday");
        assertThat(response.actionItems()).extracting("assigneeName", "suggestedUserId")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Bob", 3L), org.assertj.core.groups.Tuple.tuple("Ana", null));
        AiRequestLog log = savedLog();
        assertThat(log.getUserId()).isEqualTo(1L);
        assertThat(log.getRequestedAt()).isEqualTo(NOW);
        assertThat(log.getInputChars()).isEqualTo(NOTES.length());
        assertThat(log.getOutcome()).isEqualTo(AiOutcome.SUCCEEDED);
        assertThat(log.getLatencyMs()).isGreaterThanOrEqualTo(0);
        verify(guard).requireRole(1L, Role.OFFICER_ADMIN);
    }

    @Test
    void summarize_logRow_neverContainsNotesOrOutput() throws IllegalAccessException {
        when(llm.summarize(NOTES)).thenReturn(RESULT);

        service.summarize(1L, new SummaryRequest(NOTES, null));

        AiRequestLog log = savedLog();
        for (Field field : AiRequestLog.class.getDeclaredFields()) {
            field.setAccessible(true);
            Object value = field.get(log);
            assertThat(String.valueOf(value)).doesNotContain("social").doesNotContain("pizza");
        }
        assertThat(Arrays.stream(AiRequestLog.class.getDeclaredFields()).map(Field::getType))
                .doesNotContain((Class) String.class);
    }

    @Test
    void summarize_noCommittee_noLookupAndNoSuggestions() {
        when(llm.summarize(NOTES)).thenReturn(RESULT);

        SummaryResponse response = service.summarize(1L, new SummaryRequest(NOTES, null));

        verifyNoInteractions(taskDirectory);
        assertThat(response.actionItems()).extracting("suggestedUserId").containsOnlyNulls();
    }

    @Test
    void summarize_nullListsFromClient_becomeEmpty() {
        when(llm.summarize(NOTES)).thenReturn(new MeetingSummary("s", null, null));

        SummaryResponse response = service.summarize(1L, new SummaryRequest(NOTES, null));

        assertThat(response.decisions()).isEmpty();
        assertThat(response.actionItems()).isEmpty();
    }

    @Test
    void summarize_blankText_throws422WithoutCallingLlm() {
        assertThatThrownBy(() -> service.summarize(1L, new SummaryRequest("   ", null)))
                .isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(llm, logs);
    }

    @Test
    void summarize_over5000Chars_throws422() {
        assertThatThrownBy(() -> service.summarize(1L, new SummaryRequest("x".repeat(5001), null)))
                .isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(llm);
    }

    @Test
    void summarize_exactly5000Chars_isAllowed() {
        String text = "x".repeat(5000);
        when(llm.summarize(text)).thenReturn(RESULT);

        assertThat(service.summarize(1L, new SummaryRequest(text, null)).summary()).isNotBlank();
    }

    @Test
    void summarize_notOfficer_throwsForbidden() {
        when(guard.requireRole(2L, Role.OFFICER_ADMIN)).thenThrow(new ForbiddenOperationException("no"));

        assertThatThrownBy(() -> service.summarize(2L, new SummaryRequest(NOTES, null)))
                .isInstanceOf(ForbiddenOperationException.class);
        verifyNoInteractions(llm, logs);
    }

    @Test
    void summarize_invalidOutput_logsFailedValidationAndRethrows() {
        when(llm.summarize(NOTES)).thenThrow(new LlmInvalidOutputException("bad json"));

        assertThatThrownBy(() -> service.summarize(1L, new SummaryRequest(NOTES, null)))
                .isInstanceOf(LlmInvalidOutputException.class);
        assertThat(savedLog().getOutcome()).isEqualTo(AiOutcome.FAILED_VALIDATION);
    }

    @Test
    void summarize_llmTimeout_logsServiceError() {
        when(llm.summarize(NOTES)).thenThrow(new LlmUnavailableException("timeout", null));

        assertThatThrownBy(() -> service.summarize(1L, new SummaryRequest(NOTES, null)))
                .isInstanceOf(LlmUnavailableException.class);
        assertThat(savedLog().getOutcome()).isEqualTo(AiOutcome.SERVICE_ERROR);
    }

    @Test
    void summarize_unexpectedClientError_wrappedAsUnavailable() {
        when(llm.summarize(NOTES)).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> service.summarize(1L, new SummaryRequest(NOTES, null)))
                .isInstanceOf(LlmUnavailableException.class);
        assertThat(savedLog().getOutcome()).isEqualTo(AiOutcome.SERVICE_ERROR);
    }

    @Test
    void summarize_unknownCommittee_failsBeforeLlm() {
        when(taskDirectory.committeeMembers(9L)).thenThrow(new com.studentorgs.portal.common.error.NotFoundException("x"));

        assertThatThrownBy(() -> service.summarize(1L, new SummaryRequest(NOTES, 9L)))
                .isInstanceOf(com.studentorgs.portal.common.error.NotFoundException.class);
        verify(llm, never()).summarize(any());
        verifyNoInteractions(logs);
    }
}
