package com.studentorgs.portal.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LowercaseEnumConverterTest {

    enum TaskStatus { TODO, IN_PROGRESS, DONE }

    static class TaskStatusConverter extends LowercaseEnumConverter<TaskStatus> {
        TaskStatusConverter() {
            super(TaskStatus.class);
        }
    }

    private final TaskStatusConverter converter = new TaskStatusConverter();

    @Test
    void convertToDatabaseColumn_enum_returnsLowercase() {
        assertThat(converter.convertToDatabaseColumn(TaskStatus.IN_PROGRESS)).isEqualTo("in_progress");
    }

    @Test
    void convertToEntityAttribute_lowercase_returnsEnum() {
        assertThat(converter.convertToEntityAttribute("in_progress")).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void convert_null_returnsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_unknownValue_throws() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("archived"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
