package com.studentorgs.portal.user.service;

import static com.studentorgs.portal.user.TestUsers.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.studentorgs.portal.common.audit.AuditWriter;
import com.studentorgs.portal.common.error.BusinessRuleException;
import com.studentorgs.portal.common.error.ForbiddenOperationException;
import com.studentorgs.portal.common.error.NotFoundException;
import com.studentorgs.portal.common.paging.PageResponse;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;
import com.studentorgs.portal.user.domain.AppUser;
import com.studentorgs.portal.user.dto.RosterEntryResponse;
import com.studentorgs.portal.user.dto.UserResponse;
import com.studentorgs.portal.user.repository.AppUserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private AppUserRepository users;
    @Mock
    private AccountGuard guard;
    @Mock
    private AuditWriter auditWriter;

    @InjectMocks
    private UserService service;

    private void verifyNoAudit() {
        verify(auditWriter, never()).record(anyLong(), anyString(), anyString(), anyLong());
    }

    // --- roster ---

    @Test
    @SuppressWarnings("unchecked")
    void roster_officerAdmin_getsFullRows() {
        AppUser member = user(7, Role.MEMBER, UserStatus.ACTIVE);
        when(users.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(member), PageRequest.of(0, 20), 1));

        PageResponse<RosterEntryResponse> page = service.roster(1L, " Ana ", null, null, null);

        assertThat(page.content().get(0).email()).isEqualTo("user7@uta.edu");
        assertThat(page.content().get(0).role()).isEqualTo(Role.MEMBER);
        verify(guard).requireRole(1L, Role.OFFICER_ADMIN);
    }

    @Test
    void roster_member_throwsForbidden() {
        when(guard.requireRole(2L, Role.OFFICER_ADMIN)).thenThrow(new ForbiddenOperationException("no"));

        assertThatThrownBy(() -> service.roster(2L, null, null, 0, 20)).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void likePattern_specialCharacters_areEscaped() {
        assertThat(UserService.likePattern("50%_off\\")).isEqualTo("%50\\%\\_off\\\\%");
        assertThat(UserService.likePattern("   ")).isNull();
    }

    // --- changeRole ---

    @Test
    void changeRole_memberToOfficer_changesAndAudits() {
        AppUser target = user(7, Role.MEMBER, UserStatus.ACTIVE);
        when(users.findById(7L)).thenReturn(Optional.of(target));

        UserResponse response = service.changeRole(1L, 7L, Role.OFFICER_ADMIN);

        assertThat(response.role()).isEqualTo(Role.OFFICER_ADMIN);
        verify(guard).requireRole(1L, Role.OFFICER_ADMIN);
        verify(auditWriter).record(1L, "user.role_change", "app_user", 7L);
    }

    @Test
    void changeRole_lastActiveOfficerAdmin_throwsBusinessRule() {
        AppUser target = user(1, Role.OFFICER_ADMIN, UserStatus.ACTIVE);
        when(users.findById(1L)).thenReturn(Optional.of(target));
        when(users.countByRoleAndStatus(Role.OFFICER_ADMIN, UserStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> service.changeRole(1L, 1L, Role.MEMBER)).isInstanceOf(BusinessRuleException.class);
        assertThat(target.getRole()).isEqualTo(Role.OFFICER_ADMIN);
        verifyNoAudit();
    }

    @Test
    void changeRole_oneOfTwoOfficerAdmins_demotesToMember() {
        AppUser target = user(9, Role.OFFICER_ADMIN, UserStatus.ACTIVE);
        when(users.findById(9L)).thenReturn(Optional.of(target));
        when(users.countByRoleAndStatus(Role.OFFICER_ADMIN, UserStatus.ACTIVE)).thenReturn(2L);

        assertThat(service.changeRole(1L, 9L, Role.MEMBER).role()).isEqualTo(Role.MEMBER);
        verify(auditWriter).record(1L, "user.role_change", "app_user", 9L);
    }

    @Test
    void changeRole_sameRole_noAudit() {
        when(users.findById(7L)).thenReturn(Optional.of(user(7, Role.OFFICER_ADMIN, UserStatus.ACTIVE)));

        service.changeRole(1L, 7L, Role.OFFICER_ADMIN);

        verifyNoAudit();
    }

    @Test
    void changeRole_unknownUser_throwsNotFound() {
        when(users.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeRole(1L, 7L, Role.OFFICER_ADMIN)).isInstanceOf(NotFoundException.class);
    }

    // --- deactivate / reactivate ---

    @Test
    void deactivate_activeMemberByOfficer_deactivatesAndAudits() {
        AppUser target = user(7, Role.MEMBER, UserStatus.ACTIVE);
        when(guard.requireRole(1L, Role.OFFICER_ADMIN)).thenReturn(user(1, Role.OFFICER_ADMIN, UserStatus.ACTIVE));
        when(users.findById(7L)).thenReturn(Optional.of(target));

        assertThat(service.deactivate(1L, 7L).status()).isEqualTo(UserStatus.INACTIVE);
        verify(auditWriter).record(1L, "user.deactivate", "app_user", 7L);
    }

    @Test
    void deactivate_self_throwsBusinessRule() {
        when(guard.requireRole(1L, Role.OFFICER_ADMIN)).thenReturn(user(1, Role.OFFICER_ADMIN, UserStatus.ACTIVE));

        assertThatThrownBy(() -> service.deactivate(1L, 1L)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void deactivate_otherOfficerAdminWhileAnotherRemains_deactivates() {
        AppUser target = user(7, Role.OFFICER_ADMIN, UserStatus.ACTIVE);
        when(guard.requireRole(1L, Role.OFFICER_ADMIN)).thenReturn(user(1, Role.OFFICER_ADMIN, UserStatus.ACTIVE));
        when(users.findById(7L)).thenReturn(Optional.of(target));
        when(users.countByRoleAndStatus(Role.OFFICER_ADMIN, UserStatus.ACTIVE)).thenReturn(2L);

        assertThat(service.deactivate(1L, 7L).status()).isEqualTo(UserStatus.INACTIVE);
        verify(auditWriter).record(1L, "user.deactivate", "app_user", 7L);
    }

    @Test
    void deactivate_notOfficerAdmin_throwsForbidden() {
        when(guard.requireRole(2L, Role.OFFICER_ADMIN)).thenThrow(new ForbiddenOperationException("no"));

        assertThatThrownBy(() -> service.deactivate(2L, 7L)).isInstanceOf(ForbiddenOperationException.class);
        verifyNoAudit();
    }

    @Test
    void deactivate_pendingUser_throwsBusinessRule() {
        when(guard.requireRole(1L, Role.OFFICER_ADMIN)).thenReturn(user(1, Role.OFFICER_ADMIN, UserStatus.ACTIVE));
        when(users.findById(7L)).thenReturn(Optional.of(user(7, Role.MEMBER, UserStatus.PENDING)));

        assertThatThrownBy(() -> service.deactivate(1L, 7L)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void deactivate_lastActiveOfficerAdmin_throwsBusinessRule() {
        when(guard.requireRole(1L, Role.OFFICER_ADMIN)).thenReturn(user(1, Role.OFFICER_ADMIN, UserStatus.ACTIVE));
        when(users.findById(9L)).thenReturn(Optional.of(user(9, Role.OFFICER_ADMIN, UserStatus.ACTIVE)));
        when(users.countByRoleAndStatus(Role.OFFICER_ADMIN, UserStatus.ACTIVE)).thenReturn(1L);

        assertThatThrownBy(() -> service.deactivate(1L, 9L)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void reactivate_inactiveMember_activatesAndAudits() {
        AppUser target = user(7, Role.MEMBER, UserStatus.INACTIVE);
        when(guard.requireRole(1L, Role.OFFICER_ADMIN)).thenReturn(user(1, Role.OFFICER_ADMIN, UserStatus.ACTIVE));
        when(users.findById(7L)).thenReturn(Optional.of(target));

        assertThat(service.reactivate(1L, 7L).status()).isEqualTo(UserStatus.ACTIVE);
        verify(auditWriter).record(1L, "user.reactivate", "app_user", 7L);
    }

    @Test
    void reactivate_activeUser_throwsBusinessRule() {
        when(guard.requireRole(1L, Role.OFFICER_ADMIN)).thenReturn(user(1, Role.OFFICER_ADMIN, UserStatus.ACTIVE));
        when(users.findById(7L)).thenReturn(Optional.of(user(7, Role.MEMBER, UserStatus.ACTIVE)));

        assertThatThrownBy(() -> service.reactivate(1L, 7L)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void reactivate_inactiveOfficerAdmin_reactivates() {
        AppUser target = user(7, Role.OFFICER_ADMIN, UserStatus.INACTIVE);
        when(guard.requireRole(1L, Role.OFFICER_ADMIN)).thenReturn(user(1, Role.OFFICER_ADMIN, UserStatus.ACTIVE));
        when(users.findById(7L)).thenReturn(Optional.of(target));

        assertThat(service.reactivate(1L, 7L).status()).isEqualTo(UserStatus.ACTIVE);
    }

    // --- internal ---

    @Test
    void status_knownUser_returnsStatusAndRole() {
        when(users.findById(7L)).thenReturn(Optional.of(user(7, Role.OFFICER_ADMIN, UserStatus.ACTIVE)));

        var status = service.status(7L);

        assertThat(status.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(status.role()).isEqualTo(Role.OFFICER_ADMIN);
    }

    @Test
    void names_tooManyIds_throwsBusinessRule() {
        List<Long> ids = Collections.nCopies(101, 1L);

        assertThatThrownBy(() -> service.names(ids)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void names_ids_returnsDisplayNames() {
        when(users.findAllById(List.of(7L))).thenReturn(List.of(user(7, Role.MEMBER, UserStatus.ACTIVE)));

        assertThat(service.names(List.of(7L))).extracting("displayName").containsExactly("User 7");
    }

    @Test
    void activeIds_returnsRepositoryIds() {
        when(users.findIdsByStatus(UserStatus.ACTIVE)).thenReturn(List.of(1L, 7L));

        assertThat(service.activeIds()).containsExactly(1L, 7L);
    }
}
