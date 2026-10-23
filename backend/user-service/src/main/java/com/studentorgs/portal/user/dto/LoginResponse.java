package com.studentorgs.portal.user.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {

    public static LoginResponse bearer(String accessToken, long expiresIn, UserResponse user) {
        return new LoginResponse(accessToken, "Bearer", expiresIn, user);
    }

    @Override
    public String toString() {
        return "LoginResponse[user=" + user + "]";
    }
}
