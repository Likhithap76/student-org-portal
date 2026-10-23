package com.studentorgs.portal.user.domain;

import com.studentorgs.portal.common.persistence.LowercaseEnumConverter;
import jakarta.persistence.Converter;

@Converter
public class DecisionConverter extends LowercaseEnumConverter<Decision> {

    public DecisionConverter() {
        super(Decision.class);
    }
}
