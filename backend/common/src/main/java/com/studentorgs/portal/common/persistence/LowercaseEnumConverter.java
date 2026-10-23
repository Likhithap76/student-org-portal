package com.studentorgs.portal.common.persistence;

import java.util.Locale;

import jakarta.persistence.AttributeConverter;

/**
 * Maps an UPPER_CASE Java enum to the lowercase value stored in the DB ({@code IN_PROGRESS ↔ in_progress}).
 * Subclass once per enum and annotate the subclass with {@code @Converter}:
 * <pre>
 * {@literal @}Converter
 * public class RoleConverter extends LowercaseEnumConverter&lt;Role&gt; {
 *     public RoleConverter() { super(Role.class); }
 * }
 * </pre>
 */
public abstract class LowercaseEnumConverter<E extends Enum<E>> implements AttributeConverter<E, String> {

    private final Class<E> type;

    protected LowercaseEnumConverter(Class<E> type) {
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(E value) {
        return value == null ? null : value.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public E convertToEntityAttribute(String dbValue) {
        return dbValue == null ? null : Enum.valueOf(type, dbValue.toUpperCase(Locale.ROOT));
    }
}
