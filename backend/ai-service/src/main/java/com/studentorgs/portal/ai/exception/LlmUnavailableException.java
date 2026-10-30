package com.studentorgs.portal.ai.exception;

/** The LLM could not be reached or timed out (→ 503, outcome {@code service_error}). */
public class LlmUnavailableException extends RuntimeException {

    public LlmUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
