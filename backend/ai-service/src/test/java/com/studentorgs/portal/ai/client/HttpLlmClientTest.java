package com.studentorgs.portal.ai.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.studentorgs.portal.ai.exception.LlmUnavailableException;

class HttpLlmClientTest {

    @Test
    void summarize_notImplemented_throwsUnavailable() {
        assertThatThrownBy(() -> new HttpLlmClient().summarize("Budget review"))
                .isInstanceOf(LlmUnavailableException.class)
                .hasMessage(HttpLlmClient.NOT_CONFIGURED);
    }
}
