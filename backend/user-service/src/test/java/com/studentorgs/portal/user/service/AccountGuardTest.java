package com.studentorgs.portal.user.service;

import static com.studentorgs.portal.user.TestUsers.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.studentorgs.portal.common.error.ForbiddenOperationException;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;
import com.studentorgs.portal.user.repository.AppUserRepository;

@ExtendWith(MockitoExtension.class)
class AccountGuardTest {

    @Mock
    private AppUserRepository users;

    @InjectMocks
    private AccountGuard guard;

    @Test
    void requireUsable_pendingUser_returnsUser() {
        when(users.findById(1L)).thenReturn(Optional.of(user(1, Role.MEMBER, UserStatus.PENDING)));

        assertThat(guard.requireUsable(1L).getId()).isEqualTo(1L);
    }

    @Test
    void requireUsable_inactiveUser_throwsForbidden() {
        when(users.findById(1L)).thenReturn(Optional.of(user(1, Role.MEMBER, UserStatus.INACTIVE)));

        assertThatThrownBy(() -> guard.requireUsable(1L)).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void requireUsable_unknownUser_throwsForbidden() {
        when(users.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireUsable(1L)).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void requireActive_pendingUser_throwsForbidden() {
        when(users.findById(1L)).thenReturn(Optional.of(user(1, Role.MEMBER, UserStatus.PENDING)));

        assertThatThrownBy(() -> guard.requireActive(1L)).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void requireRole_demotedInDatabase_throwsForbidden() {
        when(users.findById(1L)).thenReturn(Optional.of(user(1, Role.MEMBER, UserStatus.ACTIVE)));

        assertThatThrownBy(() -> guard.requireRole(1L, Role.OFFICER_ADMIN)).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void requireRole_adminForOfficerAction_returnsUser() {
        when(users.findById(1L)).thenReturn(Optional.of(user(1, Role.OFFICER_ADMIN, UserStatus.ACTIVE)));

        assertThat(guard.requireRole(1L, Role.OFFICER_ADMIN).getRole()).isEqualTo(Role.OFFICER_ADMIN);
    }
}
