package com.studentorgs.portal.user.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(@NotBlank String currentPassword, @StrongPassword String newPassword) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[***]";
    }
}
