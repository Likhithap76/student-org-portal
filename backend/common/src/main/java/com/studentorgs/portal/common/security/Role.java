package com.studentorgs.portal.common.security;

/**
 * Portal roles (team DB design: only two). {@code OFFICER_ADMIN} can do everything a member can.
 * Stored lowercase in the DB: {@code member}, {@code officer_admin}.
 */
public enum Role {
    MEMBER,
    OFFICER_ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }

    /** True when this role includes {@code other} under the role hierarchy. */
    public boolean includes(Role other) {
        return compareTo(other) >= 0;
    }
}
