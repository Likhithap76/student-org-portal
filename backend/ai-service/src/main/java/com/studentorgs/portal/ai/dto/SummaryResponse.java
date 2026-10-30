package com.studentorgs.portal.ai.dto;

import java.util.List;

/** The officer reviews this in the frontend; accepted items go to task-service {@code POST /tasks/batch}. */
public record SummaryResponse(String summary, List<String> decisions, List<ActionItemResponse> actionItems) {
}
