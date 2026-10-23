package com.studentorgs.portal.notification.dto;

import java.util.List;

import com.studentorgs.portal.common.client.NotificationKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Internal body, same shape as common's {@code NotificationRequest}: one row per recipient. */
public record CreateNotificationsRequest(
        @NotEmpty @Size(max = 5000) List<@NotNull Long> recipientIds,
        @NotNull NotificationKind kind,
        @NotBlank @Size(max = 500) String message,
        @NotBlank @Size(max = 50) String referenceTable,
        @NotNull Long referenceId) {
}
