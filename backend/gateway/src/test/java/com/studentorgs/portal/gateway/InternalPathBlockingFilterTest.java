package com.studentorgs.portal.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InternalPathBlockingFilterTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "/internal/users/1/status",
            "/api/users/../internal/users/1",
            "/api/users/%2e%2e/internal/x",
            "/api/users/%2E%2E/INTERNAL/x",
            "/api/users/internal;x=1/y",
            "/api/users/..%5cinternal/x"})
    void isBlocked_internalOrTraversalPath_returnsTrue(String path) {
        assertThat(InternalPathBlockingFilter.isBlocked(path)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/events", "/api/users/roster", "/api/documents/internal-handbook", "/"})
    void isBlocked_normalPath_returnsFalse(String path) {
        assertThat(InternalPathBlockingFilter.isBlocked(path)).isFalse();
    }
}
