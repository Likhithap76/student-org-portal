package com.studentorgs.portal.user.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.studentorgs.portal.common.security.CurrentUser;
import com.studentorgs.portal.user.dto.ChangePasswordRequest;
import com.studentorgs.portal.user.dto.LoginRequest;
import com.studentorgs.portal.user.dto.LoginResponse;
import com.studentorgs.portal.user.dto.RegisterRequest;
import com.studentorgs.portal.user.dto.UpdateProfileRequest;
import com.studentorgs.portal.user.dto.UserResponse;
import com.studentorgs.portal.user.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements
    @Operation(summary = "Register: creates a pending member and a pending membership application")
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Log in and receive a 2-hour access token")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @Operation(summary = "My profile")
    public UserResponse me() {
        return authService.me(CurrentUser.id());
    }

    @PutMapping("/me")
    @Operation(summary = "Update my display name")
    public UserResponse updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return authService.updateProfile(CurrentUser.id(), request);
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Change my password (current password required)")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(CurrentUser.id(), request);
    }
}
