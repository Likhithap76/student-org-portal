package com.studentorgs.portal.task.domain;

import com.studentorgs.portal.common.persistence.LowercaseEnumConverter;
import jakarta.persistence.Converter;

@Converter
public class TaskStatusConverter extends LowercaseEnumConverter<TaskStatus> {

    public TaskStatusConverter() {
        super(TaskStatus.class);
    }
}
