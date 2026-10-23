package com.studentorgs.portal.notification.dto;

import java.time.Instant;

import com.studentorgs.portal.common.client.NotificationKind;
import com.studentorgs.portal.notification.domain.Notification;

public record NotificationResponse(
        Long id,
        NotificationKind kind,
        String message,
        String referenceTable,
        Long referenceId,
        Instant createdAt,
        Instant readAt,
        boolean read) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getKind(), n.getMessage(), n.getReferenceTable(),
                n.getReferenceId(), n.getCreatedAt(), n.getReadAt(), n.getReadAt() != null);
    }
}
