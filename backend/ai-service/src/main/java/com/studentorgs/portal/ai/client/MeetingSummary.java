package com.studentorgs.portal.ai.client;

import java.time.LocalDate;
import java.util.List;

/** What an {@link LlmClient} returns. Names and dates are only what the notes state (null otherwise). */
public record MeetingSummary(String summary, List<String> decisions, List<ActionItem> actionItems) {

    public record ActionItem(String description, String assigneeName, LocalDate dueDate) {
    }
}
