package com.studentorgs.portal.common.client;

import java.util.List;

/** Body of {@code POST /internal/notifications}: one notification row per recipient. */
public record NotificationRequest(
        List<Long> recipientIds,
        NotificationKind kind,
        String message,
        String referenceTable,
        Long referenceId) {
}
