package com.studentorgs.portal.user.dto;

import com.studentorgs.portal.user.domain.AppUser;

/** Internal: id and display name. */
public record UserNameResponse(Long id, String displayName) {

    public static UserNameResponse from(AppUser user) {
        return new UserNameResponse(user.getId(), user.getDisplayName());
    }
}
