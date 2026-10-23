package com.studentorgs.portal.common.client;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/** Kinds of in-app notification. Sent and stored lowercase. */
public enum NotificationKind {
    ANNOUNCEMENT,
    APPLICATION_DECISION,
    TASK_ASSIGNED;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
