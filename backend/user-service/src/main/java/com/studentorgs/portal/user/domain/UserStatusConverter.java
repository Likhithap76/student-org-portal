package com.studentorgs.portal.user.domain;

import com.studentorgs.portal.common.persistence.LowercaseEnumConverter;
import com.studentorgs.portal.common.security.UserStatus;
import jakarta.persistence.Converter;

@Converter
public class UserStatusConverter extends LowercaseEnumConverter<UserStatus> {

    public UserStatusConverter() {
        super(UserStatus.class);
    }
}
