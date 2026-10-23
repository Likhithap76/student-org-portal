package com.studentorgs.portal.user.service;

import java.time.Clock;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studentorgs.portal.common.audit.AuditWriter;
import com.studentorgs.portal.common.error.BusinessRuleException;
import com.studentorgs.portal.common.error.ConflictException;
import com.studentorgs.portal.common.error.NotFoundException;
import com.studentorgs.portal.common.paging.PageRequests;
import com.studentorgs.portal.common.paging.PageResponse;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.user.domain.AppUser;
import com.studentorgs.portal.user.domain.Decision;
import com.studentorgs.portal.user.domain.MembershipApplication;
import com.studentorgs.portal.user.dto.ApplicationResponse;
import com.studentorgs.portal.user.repository.AppUserRepository;
import com.studentorgs.portal.user.repository.MembershipApplicationRepository;
import lombok.RequiredArgsConstructor;

/** Membership applications: status (PB-04) and the officer review queue with approve/reject (PB-05). */
@Service
@RequiredArgsConstructor
public class MembershipService {

    static final String APPLICATION_TABLE = "membership_application";
    static final String ACTION_APPROVE = "membership.approve";
    static final String ACTION_REJECT = "membership.reject";

    private static final Sort OLDEST_FIRST = Sort.by("submittedAt").ascending().and(Sort.by("id").ascending());

    private final MembershipApplicationRepository applications;
    private final AppUserRepository users;
    private final AccountGuard guard;
    private final AuditWriter auditWriter;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ApplicationResponse myLatest(Long callerId) {
        AppUser caller = guard.requireUsable(callerId);
        MembershipApplication latest = applications.findFirstByUserIdOrderBySubmittedAtDescIdDesc(callerId)
                .orElseThrow(() -> new NotFoundException("You have no membership application"));
        return ApplicationResponse.from(latest, caller);
    }

    /** A pending (not yet approved) user whose last application was rejected applies again. */
    @Transactional
    public ApplicationResponse reapply(Long callerId) {
        AppUser caller = guard.requireUsable(callerId);
        if (caller.isActive()) {
            throw new BusinessRuleException("You are already an active member");
        }
        if (applications.existsByUserIdAndDecision(callerId, Decision.PENDING)) {
            throw new ConflictException("You already have a pending application");
        }
        MembershipApplication application = applications.save(MembershipApplication.submit(callerId, clock.instant()));
        return ApplicationResponse.from(application, caller);
    }

    @Transactional(readOnly = true)
    public PageResponse<ApplicationResponse> queue(Long callerId, Decision decision, Integer page, Integer size) {
        guard.requireRole(callerId, Role.OFFICER_ADMIN);
        Page<MembershipApplication> result = applications.findByDecision(
                decision == null ? Decision.PENDING : decision, PageRequests.of(page, size, OLDEST_FIRST));
        Map<Long, AppUser> applicants = users.findAllById(
                result.getContent().stream().map(MembershipApplication::getUserId).toList())
                .stream().collect(Collectors.toMap(AppUser::getId, Function.identity()));
        return PageResponse.from(result, a -> ApplicationResponse.from(a, applicants.get(a.getUserId())));
    }

    /** Approve: decision, decided_by/at, user → active and audit row commit together; then notify. */
    @Transactional
    public ApplicationResponse approve(Long officerId, Long applicationId) {
        guard.requireRole(officerId, Role.OFFICER_ADMIN);
        MembershipApplication application = lockApplication(applicationId);
        application.approve(officerId, clock.instant());
        AppUser applicant = applicant(application);
        applicant.activate();
        auditWriter.record(officerId, ACTION_APPROVE, APPLICATION_TABLE, applicationId);
        events.publishEvent(new ApplicationDecidedEvent(applicationId, applicant.getId(), Decision.APPROVED));
        return ApplicationResponse.from(application, applicant);
    }

    /** Reject: the user stays pending and may reapply. */
    @Transactional
    public ApplicationResponse reject(Long officerId, Long applicationId) {
        guard.requireRole(officerId, Role.OFFICER_ADMIN);
        MembershipApplication application = lockApplication(applicationId);
        application.reject(officerId, clock.instant());
        AppUser applicant = applicant(application);
        auditWriter.record(officerId, ACTION_REJECT, APPLICATION_TABLE, applicationId);
        events.publishEvent(new ApplicationDecidedEvent(applicationId, applicant.getId(), Decision.REJECTED));
        return ApplicationResponse.from(application, applicant);
    }

    private MembershipApplication lockApplication(Long applicationId) {
        return applications.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new NotFoundException("Application " + applicationId + " not found"));
    }

    private AppUser applicant(MembershipApplication application) {
        return users.findById(application.getUserId())
                .orElseThrow(() -> new NotFoundException("Applicant " + application.getUserId() + " not found"));
    }
}
