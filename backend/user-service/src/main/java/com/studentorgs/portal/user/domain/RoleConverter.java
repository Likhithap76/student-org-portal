package com.studentorgs.portal.user.domain;

import com.studentorgs.portal.common.persistence.LowercaseEnumConverter;
import com.studentorgs.portal.common.security.Role;
import jakarta.persistence.Converter;

@Converter
public class RoleConverter extends LowercaseEnumConverter<Role> {

    public RoleConverter() {
        super(Role.class);
    }
}
