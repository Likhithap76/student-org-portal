package com.studentorgs.portal.ai.client;

import com.studentorgs.portal.ai.exception.LlmInvalidOutputException;
import com.studentorgs.portal.ai.exception.LlmUnavailableException;

/**
 * The hand-off point for the AI teammate: turn meeting notes into a {@link MeetingSummary}.
 *
 * <p>Contract for implementations:
 * <ul>
 *   <li>Return a validated summary (the service trusts its shape).</li>
 *   <li>Throw {@link LlmUnavailableException} on timeout / provider errors (→ 503, outcome {@code service_error}).</li>
 *   <li>Throw {@link LlmInvalidOutputException} when the model's answer is unusable after any retry
 *       (→ 502, outcome {@code failed_validation}).</li>
 *   <li>Never log the notes or the model's answer.</li>
 * </ul>
 * {@link HttpLlmClient} is the template to fill in.
 */
public interface LlmClient {

    MeetingSummary summarize(String notes);
}
