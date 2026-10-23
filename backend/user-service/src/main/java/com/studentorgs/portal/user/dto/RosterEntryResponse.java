package com.studentorgs.portal.user.dto;

import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.user.domain.AppUser;

/** Roster row (officer_admin only). */
public record RosterEntryResponse(Long id, String displayName, String email, Role role) {

    public static RosterEntryResponse from(AppUser user) {
        return new RosterEntryResponse(user.getId(), user.getDisplayName(), user.getEmail(), user.getRole());
    }
}
