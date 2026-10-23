package com.studentorgs.portal.user.service;

import java.time.Clock;
import java.util.Locale;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studentorgs.portal.common.error.BusinessRuleException;
import com.studentorgs.portal.common.error.ConflictException;
import com.studentorgs.portal.common.error.ForbiddenOperationException;
import com.studentorgs.portal.common.security.AuthenticatedUser;
import com.studentorgs.portal.common.security.JwtService;
import com.studentorgs.portal.common.security.UserStatus;
import com.studentorgs.portal.user.domain.AppUser;
import com.studentorgs.portal.user.domain.MembershipApplication;
import com.studentorgs.portal.user.dto.ChangePasswordRequest;
import com.studentorgs.portal.user.dto.LoginRequest;
import com.studentorgs.portal.user.dto.LoginResponse;
import com.studentorgs.portal.user.dto.RegisterRequest;
import com.studentorgs.portal.user.dto.UpdateProfileRequest;
import com.studentorgs.portal.user.dto.UserResponse;
import com.studentorgs.portal.user.repository.AppUserRepository;
import com.studentorgs.portal.user.repository.MembershipApplicationRepository;

/** Registration, login and the caller's own profile (PB-02). */
@Service
public class AuthService {

    private final AppUserRepository users;
    private final MembershipApplicationRepository applications;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AccountGuard guard;
    private final Clock clock;
    /** Compared against when the email is unknown, so both failure paths cost one BCrypt check. */
    private final String dummyHash;

    public AuthService(AppUserRepository users, MembershipApplicationRepository applications,
            PasswordEncoder passwordEncoder, JwtService jwtService, AccountGuard guard, Clock clock) {
        this.users = users;
        this.applications = applications;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.guard = guard;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    /** Creates a pending member and their pending application in one transaction. */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        AppUser user = users.save(AppUser.newApplicant(
                email, request.displayName().trim(), passwordEncoder.encode(request.password()), clock.instant()));
        applications.save(MembershipApplication.submit(user.getId(), clock.instant()));
        return UserResponse.from(user);
    }

    /** Pending and active users get a token; inactive users get 403; bad credentials get a generic 401. */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        AppUser user = users.findByEmail(normalizeEmail(request.email())).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw new BadCredentialsException("Invalid email or password");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new ForbiddenOperationException("This account is inactive. Contact an officer_admin to reactivate it.");
        }
        String token = jwtService.issue(new AuthenticatedUser(
                user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(), user.getStatus()));
        return LoginResponse.bearer(token, jwtService.expiresInSeconds(), UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long callerId) {
        return UserResponse.from(guard.requireUsable(callerId));
    }

    @Transactional
    public UserResponse updateProfile(Long callerId, UpdateProfileRequest request) {
        AppUser user = guard.requireUsable(callerId);
        user.changeDisplayName(request.displayName().trim());
        return UserResponse.from(user);
    }

    @Transactional
    public void changePassword(Long callerId, ChangePasswordRequest request) {
        AppUser user = guard.requireUsable(callerId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("Current password is incorrect");
        }
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
