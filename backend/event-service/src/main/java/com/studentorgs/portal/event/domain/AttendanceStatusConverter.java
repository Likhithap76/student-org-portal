package com.studentorgs.portal.event.domain;

import com.studentorgs.portal.common.persistence.LowercaseEnumConverter;
import jakarta.persistence.Converter;

@Converter
public class AttendanceStatusConverter extends LowercaseEnumConverter<AttendanceStatus> {

    public AttendanceStatusConverter() {
        super(AttendanceStatus.class);
    }
}
