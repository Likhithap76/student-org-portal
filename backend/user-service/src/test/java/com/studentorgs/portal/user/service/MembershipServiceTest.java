package com.studentorgs.portal.user.service;

import static com.studentorgs.portal.user.TestUsers.NOW;
import static com.studentorgs.portal.user.TestUsers.pendingApplication;
import static com.studentorgs.portal.user.TestUsers.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.studentorgs.portal.common.audit.AuditWriter;
import com.studentorgs.portal.common.error.BusinessRuleException;
import com.studentorgs.portal.common.error.ConflictException;
import com.studentorgs.portal.common.error.ForbiddenOperationException;
import com.studentorgs.portal.common.error.NotFoundException;
import com.studentorgs.portal.common.paging.PageResponse;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;
import com.studentorgs.portal.user.domain.AppUser;
import com.studentorgs.portal.user.domain.Decision;
import com.studentorgs.portal.user.domain.MembershipApplication;
import com.studentorgs.portal.user.dto.ApplicationResponse;
import com.studentorgs.portal.user.repository.AppUserRepository;
import com.studentorgs.portal.user.repository.MembershipApplicationRepository;

@ExtendWith(MockitoExtension.class)
class MembershipServiceTest {

    private static final long OFFICER_ADMIN_ID = 1L;
    private static final long APPLICANT = 5L;
    private static final long APPLICATION = 50L;

    @Mock
    private MembershipApplicationRepository applications;
    @Mock
    private AppUserRepository users;
    @Mock
    private AccountGuard guard;
    @Mock
    private AuditWriter auditWriter;
    @Mock
    private ApplicationEventPublisher events;

    private MembershipService service;

    @BeforeEach
    void setUp() {
        service = new MembershipService(applications, users, guard, auditWriter, events, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void approve_pendingApplication_decidesActivatesAuditsAndPublishes() {
        AppUser applicant = user(APPLICANT, Role.MEMBER, UserStatus.PENDING);
        when(applications.findByIdForUpdate(APPLICATION)).thenReturn(Optional.of(pendingApplication(APPLICATION, APPLICANT)));
        when(users.findById(APPLICANT)).thenReturn(Optional.of(applicant));

        ApplicationResponse response = service.approve(OFFICER_ADMIN_ID, APPLICATION);

        assertThat(response.decision()).isEqualTo(Decision.APPROVED);
        assertThat(response.decidedBy()).isEqualTo(OFFICER_ADMIN_ID);
        assertThat(response.decidedAt()).isEqualTo(NOW);
        assertThat(applicant.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(guard).requireRole(OFFICER_ADMIN_ID, Role.OFFICER_ADMIN);
        verify(auditWriter).record(OFFICER_ADMIN_ID, "membership.approve", "membership_application", APPLICATION);
        verify(events).publishEvent(new ApplicationDecidedEvent(APPLICATION, APPLICANT, Decision.APPROVED));
    }

    @Test
    void approve_alreadyDecided_throwsBusinessRuleWithoutAudit() {
        MembershipApplication decided = pendingApplication(APPLICATION, APPLICANT);
        decided.reject(OFFICER_ADMIN_ID, NOW);
        when(applications.findByIdForUpdate(APPLICATION)).thenReturn(Optional.of(decided));

        assertThatThrownBy(() -> service.approve(OFFICER_ADMIN_ID, APPLICATION)).isInstanceOf(BusinessRuleException.class);
        verify(auditWriter, never()).record(anyLong(), anyString(), anyString(), anyLong());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void approve_unknownApplication_throwsNotFound() {
        when(applications.findByIdForUpdate(APPLICATION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(OFFICER_ADMIN_ID, APPLICATION)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void approve_callerNotOfficer_throwsForbidden() {
        when(guard.requireRole(OFFICER_ADMIN_ID, Role.OFFICER_ADMIN)).thenThrow(new ForbiddenOperationException("no"));

        assertThatThrownBy(() -> service.approve(OFFICER_ADMIN_ID, APPLICATION)).isInstanceOf(ForbiddenOperationException.class);
        verify(applications, never()).findByIdForUpdate(any());
    }

    @Test
    void reject_pendingApplication_userStaysPending() {
        AppUser applicant = user(APPLICANT, Role.MEMBER, UserStatus.PENDING);
        when(applications.findByIdForUpdate(APPLICATION)).thenReturn(Optional.of(pendingApplication(APPLICATION, APPLICANT)));
        when(users.findById(APPLICANT)).thenReturn(Optional.of(applicant));

        ApplicationResponse response = service.reject(OFFICER_ADMIN_ID, APPLICATION);

        assertThat(response.decision()).isEqualTo(Decision.REJECTED);
        assertThat(response.decidedBy()).isEqualTo(OFFICER_ADMIN_ID);
        assertThat(applicant.getStatus()).isEqualTo(UserStatus.PENDING);
        verify(auditWriter).record(OFFICER_ADMIN_ID, "membership.reject", "membership_application", APPLICATION);
        verify(events).publishEvent(new ApplicationDecidedEvent(APPLICATION, APPLICANT, Decision.REJECTED));
    }

    @Test
    void myLatest_hasApplication_returnsIt() {
        when(guard.requireUsable(APPLICANT)).thenReturn(user(APPLICANT, Role.MEMBER, UserStatus.PENDING));
        when(applications.findFirstByUserIdOrderBySubmittedAtDescIdDesc(APPLICANT))
                .thenReturn(Optional.of(pendingApplication(APPLICATION, APPLICANT)));

        ApplicationResponse response = service.myLatest(APPLICANT);

        assertThat(response.id()).isEqualTo(APPLICATION);
        assertThat(response.applicantEmail()).isEqualTo("user5@uta.edu");
    }

    @Test
    void myLatest_noApplication_throwsNotFound() {
        when(guard.requireUsable(APPLICANT)).thenReturn(user(APPLICANT, Role.MEMBER, UserStatus.ACTIVE));
        when(applications.findFirstByUserIdOrderBySubmittedAtDescIdDesc(APPLICANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.myLatest(APPLICANT)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void reapply_rejectedPendingUser_createsNewPendingApplication() {
        when(guard.requireUsable(APPLICANT)).thenReturn(user(APPLICANT, Role.MEMBER, UserStatus.PENDING));
        when(applications.existsByUserIdAndDecision(APPLICANT, Decision.PENDING)).thenReturn(false);
        when(applications.save(any(MembershipApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        ApplicationResponse response = service.reapply(APPLICANT);

        assertThat(response.decision()).isEqualTo(Decision.PENDING);
        assertThat(response.userId()).isEqualTo(APPLICANT);
        assertThat(response.submittedAt()).isEqualTo(NOW);
    }

    @Test
    void reapply_alreadyPending_throwsConflict() {
        when(guard.requireUsable(APPLICANT)).thenReturn(user(APPLICANT, Role.MEMBER, UserStatus.PENDING));
        when(applications.existsByUserIdAndDecision(APPLICANT, Decision.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> service.reapply(APPLICANT)).isInstanceOf(ConflictException.class);
        verify(applications, never()).save(any());
    }

    @Test
    void reapply_activeMember_throwsBusinessRule() {
        when(guard.requireUsable(APPLICANT)).thenReturn(user(APPLICANT, Role.MEMBER, UserStatus.ACTIVE));

        assertThatThrownBy(() -> service.reapply(APPLICANT)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void queue_pending_returnsOldestFirstWithApplicantNames() {
        MembershipApplication first = pendingApplication(51L, APPLICANT);
        when(applications.findByDecision(eq(Decision.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first), PageRequest.of(0, 20), 1));
        when(users.findAllById(List.of(APPLICANT))).thenReturn(List.of(user(APPLICANT, Role.MEMBER, UserStatus.PENDING)));

        PageResponse<ApplicationResponse> page = service.queue(OFFICER_ADMIN_ID, null, null, null);

        assertThat(page.content()).extracting(ApplicationResponse::applicantName).containsExactly("User 5");
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(applications).findByDecision(eq(Decision.PENDING), pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("submittedAt").isAscending()).isTrue();
    }
}
