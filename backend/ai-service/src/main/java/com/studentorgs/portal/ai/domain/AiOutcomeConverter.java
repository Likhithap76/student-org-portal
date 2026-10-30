package com.studentorgs.portal.ai.domain;

import com.studentorgs.portal.common.persistence.LowercaseEnumConverter;
import jakarta.persistence.Converter;

@Converter
public class AiOutcomeConverter extends LowercaseEnumConverter<AiOutcome> {

    public AiOutcomeConverter() {
        super(AiOutcome.class);
    }
}
