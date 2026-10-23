package com.studentorgs.portal.task.domain;

import com.studentorgs.portal.common.persistence.LowercaseEnumConverter;
import jakarta.persistence.Converter;

@Converter
public class CommitteeKindConverter extends LowercaseEnumConverter<CommitteeKind> {

    public CommitteeKindConverter() {
        super(CommitteeKind.class);
    }
}
