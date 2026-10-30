package com.studentorgs.portal.ai.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.studentorgs.portal.ai.client.LlmClient;
import com.studentorgs.portal.ai.client.MeetingSummary;
import com.studentorgs.portal.ai.client.TaskDirectoryClient;
import com.studentorgs.portal.ai.client.TaskDirectoryClient.CommitteeMember;
import com.studentorgs.portal.ai.domain.AiOutcome;
import com.studentorgs.portal.ai.domain.AiRequestLog;
import com.studentorgs.portal.ai.dto.ActionItemResponse;
import com.studentorgs.portal.ai.dto.SummaryRequest;
import com.studentorgs.portal.ai.dto.SummaryResponse;
import com.studentorgs.portal.ai.exception.LlmInvalidOutputException;
import com.studentorgs.portal.ai.exception.LlmUnavailableException;
import com.studentorgs.portal.ai.repository.AiRequestLogRepository;
import com.studentorgs.portal.common.error.BusinessRuleException;
import com.studentorgs.portal.common.security.MemberGuard;
import com.studentorgs.portal.common.security.Role;
import lombok.RequiredArgsConstructor;

/**
 * Meeting notes → summary, decisions and suggested tasks. Stateless: it stores one log row per LLM call
 * and never stores or logs the notes or the answer. It never creates tasks; the officer does that via
 * task-service after reviewing.
 */
@Service
@RequiredArgsConstructor
public class AiSummaryService {

    static final int MAX_CHARS = 5000;

    private final LlmClient llm;
    private final TaskDirectoryClient taskDirectory;
    private final AiRequestLogRepository logs;
    private final MemberGuard guard;
    private final Clock clock;

    public SummaryResponse summarize(Long officerId, SummaryRequest request) {
        guard.requireRole(officerId, Role.OFFICER_ADMIN);
        String text = request.text();
        if (text.isBlank() || text.length() > MAX_CHARS) {
            throw new BusinessRuleException("Notes must be between 1 and " + MAX_CHARS + " characters");
        }
        List<CommitteeMember> members = request.committeeId() == null
                ? List.of() : taskDirectory.committeeMembers(request.committeeId());

        Instant requestedAt = clock.instant();
        long start = System.nanoTime();
        MeetingSummary summary;
        try {
            summary = llm.summarize(text);
        } catch (LlmInvalidOutputException e) {
            log(officerId, requestedAt, text, AiOutcome.FAILED_VALIDATION, start);
            throw e;
        } catch (LlmUnavailableException e) {
            log(officerId, requestedAt, text, AiOutcome.SERVICE_ERROR, start);
            throw e;
        } catch (RuntimeException e) {
            log(officerId, requestedAt, text, AiOutcome.SERVICE_ERROR, start);
            throw new LlmUnavailableException("LLM client failed", e);
        }
        log(officerId, requestedAt, text, AiOutcome.SUCCEEDED, start);
        return toResponse(summary, members);
    }

    private void log(Long userId, Instant requestedAt, String text, AiOutcome outcome, long startNanos) {
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
        logs.save(new AiRequestLog(userId, requestedAt, text.length(), outcome,
                (int) Math.min(latencyMs, Integer.MAX_VALUE)));
    }

    private static SummaryResponse toResponse(MeetingSummary summary, List<CommitteeMember> members) {
        List<ActionItemResponse> items = summary.actionItems() == null ? List.of() : summary.actionItems().stream()
                .map(i -> new ActionItemResponse(i.description(), i.assigneeName(),
                        AssigneeMatcher.match(i.assigneeName(), members), i.dueDate()))
                .toList();
        return new SummaryResponse(summary.summary(),
                summary.decisions() == null ? List.of() : summary.decisions(), items);
    }
}
