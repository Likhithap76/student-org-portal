package com.studentorgs.portal.user.service;

import static com.studentorgs.portal.user.TestUsers.NOW;
import static com.studentorgs.portal.user.TestUsers.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.studentorgs.portal.common.error.BusinessRuleException;
import com.studentorgs.portal.common.error.ConflictException;
import com.studentorgs.portal.common.error.ForbiddenOperationException;
import com.studentorgs.portal.common.security.AuthenticatedUser;
import com.studentorgs.portal.common.security.JwtService;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;
import com.studentorgs.portal.user.domain.AppUser;
import com.studentorgs.portal.user.domain.Decision;
import com.studentorgs.portal.user.domain.MembershipApplication;
import com.studentorgs.portal.user.dto.ChangePasswordRequest;
import com.studentorgs.portal.user.dto.LoginRequest;
import com.studentorgs.portal.user.dto.LoginResponse;
import com.studentorgs.portal.user.dto.RegisterRequest;
import com.studentorgs.portal.user.dto.UpdateProfileRequest;
import com.studentorgs.portal.user.dto.UserResponse;
import com.studentorgs.portal.user.repository.AppUserRepository;
import com.studentorgs.portal.user.repository.MembershipApplicationRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    // Low cost factor keeps the tests fast.
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final JwtService jwtService = new JwtService("unit-test-secret-unit-test-secret-1234", Duration.ofHours(2), clock);

    @Mock
    private AppUserRepository users;
    @Mock
    private MembershipApplicationRepository applications;
    @Mock
    private AccountGuard guard;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, applications, encoder, jwtService, guard, clock);
    }

    private AppUser withPassword(AppUser user, String raw) {
        ReflectionTestUtils.setField(user, "passwordHash", encoder.encode(raw));
        return user;
    }

    @Test
    void register_newEmail_createsPendingUserAndPendingApplication() {
        when(users.existsByEmail("ana@uta.edu")).thenReturn(false);
        when(users.save(any(AppUser.class))).thenAnswer(inv -> {
            AppUser saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 10L);
            return saved;
        });

        UserResponse response = service.register(new RegisterRequest("  Ana@UTA.edu ", " Ana ", "secret123"));

        assertThat(response.email()).isEqualTo("ana@uta.edu");
        assertThat(response.displayName()).isEqualTo("Ana");
        assertThat(response.role()).isEqualTo(Role.MEMBER);
        assertThat(response.status()).isEqualTo(UserStatus.PENDING);
        ArgumentCaptor<AppUser> savedUser = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(savedUser.capture());
        assertThat(encoder.matches("secret123", savedUser.getValue().getPasswordHash())).isTrue();
        ArgumentCaptor<MembershipApplication> savedApp = ArgumentCaptor.forClass(MembershipApplication.class);
        verify(applications).save(savedApp.capture());
        assertThat(savedApp.getValue().getUserId()).isEqualTo(10L);
        assertThat(savedApp.getValue().getDecision()).isEqualTo(Decision.PENDING);
    }

    @Test
    void register_duplicateEmail_throwsConflict() {
        when(users.existsByEmail("ana@uta.edu")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("ana@uta.edu", "Ana", "secret123")))
                .isInstanceOf(ConflictException.class);
        verify(users, never()).save(any());
        verify(applications, never()).save(any());
    }

    @Test
    void login_correctPassword_returnsTokenWithCurrentStatus() {
        AppUser ana = withPassword(user(3, Role.MEMBER, UserStatus.PENDING), "secret123");
        when(users.findByEmail("user3@uta.edu")).thenReturn(Optional.of(ana));

        LoginResponse response = service.login(new LoginRequest("USER3@uta.edu", "secret123"));

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(7200);
        AuthenticatedUser claims = jwtService.parse(response.accessToken());
        assertThat(claims.id()).isEqualTo(3L);
        assertThat(claims.status()).isEqualTo(UserStatus.PENDING);
    }

    @Test
    void login_wrongPassword_throwsBadCredentials() {
        AppUser ana = withPassword(user(3, Role.MEMBER, UserStatus.ACTIVE), "secret123");
        when(users.findByEmail("user3@uta.edu")).thenReturn(Optional.of(ana));

        assertThatThrownBy(() -> service.login(new LoginRequest("user3@uta.edu", "wrong999")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_unknownEmail_throwsSameBadCredentials() {
        when(users.findByEmail("nobody@uta.edu")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("nobody@uta.edu", "secret123")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void login_inactiveUserCorrectPassword_throwsForbidden() {
        AppUser ana = withPassword(user(3, Role.MEMBER, UserStatus.INACTIVE), "secret123");
        when(users.findByEmail("user3@uta.edu")).thenReturn(Optional.of(ana));

        assertThatThrownBy(() -> service.login(new LoginRequest("user3@uta.edu", "secret123")))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void login_inactiveUserWrongPassword_throwsBadCredentialsNotForbidden() {
        AppUser ana = withPassword(user(3, Role.MEMBER, UserStatus.INACTIVE), "secret123");
        when(users.findByEmail("user3@uta.edu")).thenReturn(Optional.of(ana));

        assertThatThrownBy(() -> service.login(new LoginRequest("user3@uta.edu", "nope1234")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void me_usableUser_returnsProfile() {
        when(guard.requireUsable(3L)).thenReturn(user(3, Role.MEMBER, UserStatus.ACTIVE));

        assertThat(service.me(3L).id()).isEqualTo(3L);
    }

    @Test
    void updateProfile_newName_changesDisplayName() {
        AppUser ana = user(3, Role.MEMBER, UserStatus.ACTIVE);
        when(guard.requireUsable(3L)).thenReturn(ana);

        UserResponse response = service.updateProfile(3L, new UpdateProfileRequest("  Ana B  "));

        assertThat(response.displayName()).isEqualTo("Ana B");
        assertThat(ana.getDisplayName()).isEqualTo("Ana B");
    }

    @Test
    void changePassword_correctCurrent_storesNewHash() {
        AppUser ana = withPassword(user(3, Role.MEMBER, UserStatus.ACTIVE), "secret123");
        when(guard.requireUsable(3L)).thenReturn(ana);

        service.changePassword(3L, new ChangePasswordRequest("secret123", "newpass456"));

        assertThat(encoder.matches("newpass456", ana.getPasswordHash())).isTrue();
    }

    @Test
    void changePassword_wrongCurrent_throwsBusinessRule() {
        AppUser ana = withPassword(user(3, Role.MEMBER, UserStatus.ACTIVE), "secret123");
        when(guard.requireUsable(3L)).thenReturn(ana);

        assertThatThrownBy(() -> service.changePassword(3L, new ChangePasswordRequest("wrong", "newpass456")))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(encoder.matches("secret123", ana.getPasswordHash())).isTrue();
    }
}
