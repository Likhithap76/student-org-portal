package com.studentorgs.portal.ai.dto;

import java.time.LocalDate;

/** A suggested task. {@code suggestedUserId} is set only when the name matches exactly one committee member. */
public record ActionItemResponse(String description, String assigneeName, Long suggestedUserId, LocalDate dueDate) {
}
