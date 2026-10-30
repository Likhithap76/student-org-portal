package com.studentorgs.portal.ai.client;

import org.springframework.stereotype.Component;

import com.studentorgs.portal.ai.exception.LlmUnavailableException;

/**
 * Template for the real LLM client. Implement {@link #summarize(String)}; see
 * {@code ai-service/README.md} and the contract in {@link LlmClient}.
 *
 * <p>Until then every call fails with {@link LlmUnavailableException}, so the endpoint answers 503
 * ("The AI service is unavailable") and logs outcome {@code service_error}.
 *
 * <p>Settings are already wired in {@code application.yml}: {@code orgportal.ai.base-url}, {@code model},
 * {@code api-key} and {@code timeout} (from {@code LLM_BASE_URL}, {@code LLM_MODEL}, {@code LLM_API_KEY}).
 */
@Component
public class HttpLlmClient implements LlmClient {

    static final String NOT_CONFIGURED = "AI provider not configured";

    @Override
    public MeetingSummary summarize(String notes) {
        // TODO: call the LLM and return a MeetingSummary.
        //  Timeout or provider error -> LlmUnavailableException; unusable answer -> LlmInvalidOutputException.
        //  Never log the notes or the model's answer.
        throw new LlmUnavailableException(NOT_CONFIGURED, null);
    }
}
