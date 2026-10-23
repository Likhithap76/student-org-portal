package com.studentorgs.portal.event.domain;

import com.studentorgs.portal.common.persistence.LowercaseEnumConverter;
import jakarta.persistence.Converter;

@Converter
public class EventStatusConverter extends LowercaseEnumConverter<EventStatus> {

    public EventStatusConverter() {
        super(EventStatus.class);
    }
}
