package com.studentorgs.portal.notification.domain;

import com.studentorgs.portal.common.client.NotificationKind;
import com.studentorgs.portal.common.persistence.LowercaseEnumConverter;
import jakarta.persistence.Converter;

@Converter
public class NotificationKindConverter extends LowercaseEnumConverter<NotificationKind> {

    public NotificationKindConverter() {
        super(NotificationKind.class);
    }
}
