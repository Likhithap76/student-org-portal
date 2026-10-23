package com.studentorgs.portal.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.studentorgs.portal.common.client.UserDirectoryClient;
import com.studentorgs.portal.common.client.UserDirectoryClient.UserStatusInfo;
import com.studentorgs.portal.common.error.ForbiddenOperationException;
import com.studentorgs.portal.common.error.ServiceUnavailableException;

@ExtendWith(MockitoExtension.class)
class MemberGuardTest {

    @Mock
    private UserDirectoryClient users;

    @InjectMocks
    private MemberGuard guard;

    @Test
    void requireActive_activeUser_returnsInfo() {
        when(users.status(1L)).thenReturn(Optional.of(new UserStatusInfo(UserStatus.ACTIVE, Role.MEMBER)));

        assertThat(guard.requireActive(1L).role()).isEqualTo(Role.MEMBER);
    }

    @Test
    void requireActive_pendingUser_throwsForbidden() {
        when(users.status(1L)).thenReturn(Optional.of(new UserStatusInfo(UserStatus.PENDING, Role.MEMBER)));

        assertThatThrownBy(() -> guard.requireActive(1L)).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void requireActive_unknownUser_throwsForbidden() {
        when(users.status(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireActive(1L)).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void requireActive_userServiceDown_propagates503() {
        when(users.status(1L)).thenThrow(new ServiceUnavailableException("User service unavailable", null));

        assertThatThrownBy(() -> guard.requireActive(1L)).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void requireRole_demotedOfficer_throwsForbidden() {
        when(users.status(1L)).thenReturn(Optional.of(new UserStatusInfo(UserStatus.ACTIVE, Role.MEMBER)));

        assertThatThrownBy(() -> guard.requireRole(1L, Role.OFFICER_ADMIN)).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void requireRole_adminForOfficerAction_passes() {
        when(users.status(1L)).thenReturn(Optional.of(new UserStatusInfo(UserStatus.ACTIVE, Role.OFFICER_ADMIN)));

        assertThat(guard.requireRole(1L, Role.OFFICER_ADMIN).role()).isEqualTo(Role.OFFICER_ADMIN);
    }

    @Test
    void requireNotInactive_pendingUser_passes() {
        when(users.status(1L)).thenReturn(Optional.of(new UserStatusInfo(UserStatus.PENDING, Role.MEMBER)));

        assertThat(guard.requireNotInactive(1L).status()).isEqualTo(UserStatus.PENDING);
    }

    @Test
    void requireNotInactive_inactiveUser_throwsForbidden() {
        when(users.status(1L)).thenReturn(Optional.of(new UserStatusInfo(UserStatus.INACTIVE, Role.MEMBER)));

        assertThatThrownBy(() -> guard.requireNotInactive(1L)).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void requireActive_inactiveUser_throwsForbidden() {
        when(users.status(1L)).thenReturn(Optional.of(new UserStatusInfo(UserStatus.INACTIVE, Role.MEMBER)));

        assertThatThrownBy(() -> guard.requireActive(1L)).isInstanceOf(ForbiddenOperationException.class);
    }
}
