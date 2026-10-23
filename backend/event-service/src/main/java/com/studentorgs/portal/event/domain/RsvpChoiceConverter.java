package com.studentorgs.portal.event.domain;

import com.studentorgs.portal.common.persistence.LowercaseEnumConverter;
import jakarta.persistence.Converter;

@Converter
public class RsvpChoiceConverter extends LowercaseEnumConverter<RsvpChoice> {

    public RsvpChoiceConverter() {
        super(RsvpChoice.class);
    }
}
