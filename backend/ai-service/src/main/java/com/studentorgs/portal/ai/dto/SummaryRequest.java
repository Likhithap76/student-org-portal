package com.studentorgs.portal.ai.dto;

import jakarta.validation.constraints.NotNull;

/** Meeting notes (1–5,000 characters, checked by the service → 422) and an optional committee for name matching. */
public record SummaryRequest(@NotNull String text, Long committeeId) {

    @Override
    public String toString() {
        return "SummaryRequest[chars=" + (text == null ? 0 : text.length()) + ", committeeId=" + committeeId + "]";
    }
}
