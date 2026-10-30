package com.studentorgs.portal.ai.exception;

/** The LLM answered, but the answer was unusable even after a retry (→ 502, outcome {@code failed_validation}). */
public class LlmInvalidOutputException extends RuntimeException {

    public LlmInvalidOutputException(String message) {
        super(message);
    }
}
