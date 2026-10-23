package com.studentorgs.portal.common.security;

/** Account status of an {@code app_user}. Stored lowercase in the DB. */
public enum UserStatus {
    PENDING,
    ACTIVE,
    INACTIVE
}
