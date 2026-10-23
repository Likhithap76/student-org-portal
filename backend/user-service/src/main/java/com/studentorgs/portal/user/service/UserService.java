package com.studentorgs.portal.user.service;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studentorgs.portal.common.audit.AuditWriter;
import com.studentorgs.portal.common.error.BusinessRuleException;
import com.studentorgs.portal.common.error.NotFoundException;
import com.studentorgs.portal.common.paging.PageRequests;
import com.studentorgs.portal.common.paging.PageResponse;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;
import com.studentorgs.portal.user.domain.AppUser;
import com.studentorgs.portal.user.dto.RosterEntryResponse;
import com.studentorgs.portal.user.dto.UserNameResponse;
import com.studentorgs.portal.user.dto.UserResponse;
import com.studentorgs.portal.user.dto.UserStatusResponse;
import com.studentorgs.portal.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;

/** Roster (PB-05), roles and account status (PB-03), and lookups for other services. */
@Service
@RequiredArgsConstructor
public class UserService {

    static final String USER_TABLE = "app_user";
    static final String ACTION_ROLE_CHANGE = "user.role_change";
    static final String ACTION_DEACTIVATE = "user.deactivate";
    static final String ACTION_REACTIVATE = "user.reactivate";
    static final int MAX_BATCH = 100;

    private static final Sort BY_NAME = Sort.by("displayName").ascending().and(Sort.by("id").ascending());

    private final AppUserRepository users;
    private final AccountGuard guard;
    private final AuditWriter auditWriter;

    /** OFFICER_ADMIN only (team decision): active users with email and role. */
    @Transactional(readOnly = true)
    public PageResponse<RosterEntryResponse> roster(Long callerId, String q, Role role, Integer page, Integer size) {
        guard.requireRole(callerId, Role.OFFICER_ADMIN);
        return PageResponse.from(
                users.findAll(AppUserRepository.hasStatus(UserStatus.ACTIVE)
                        .and(AppUserRepository.hasRole(role))
                        .and(AppUserRepository.nameOrEmailLike(likePattern(q))), PageRequests.of(page, size, BY_NAME)),
                RosterEntryResponse::from);
    }

    /** OFFICER_ADMIN: member ↔ officer_admin. The last active officer_admin cannot be demoted. */
    @Transactional
    public UserResponse changeRole(Long adminId, Long targetId, Role newRole) {
        guard.requireRole(adminId, Role.OFFICER_ADMIN);
        AppUser target = find(targetId);
        if (target.getRole() == newRole) {
            return UserResponse.from(target);
        }
        if (target.getRole() == Role.OFFICER_ADMIN && target.isActive() && isLastActiveOfficerAdmin()) {
            throw new BusinessRuleException("Cannot demote the last officer_admin");
        }
        target.changeRole(newRole);
        auditWriter.record(adminId, ACTION_ROLE_CHANGE, USER_TABLE, targetId);
        return UserResponse.from(target);
    }

    /** OFFICER_ADMIN. Only active users; not yourself; never the last active officer_admin. */
    @Transactional
    public UserResponse deactivate(Long officerId, Long targetId) {
        guard.requireRole(officerId, Role.OFFICER_ADMIN);
        if (officerId.equals(targetId)) {
            throw new BusinessRuleException("You cannot deactivate your own account");
        }
        AppUser target = find(targetId);
        if (!target.isActive()) {
            throw new BusinessRuleException("Only active users can be deactivated");
        }
        if (target.getRole() == Role.OFFICER_ADMIN && isLastActiveOfficerAdmin()) {
            throw new BusinessRuleException("Cannot deactivate the last officer_admin");
        }
        target.deactivate();
        auditWriter.record(officerId, ACTION_DEACTIVATE, USER_TABLE, targetId);
        return UserResponse.from(target);
    }

    /** OFFICER_ADMIN. Inactive → active. */
    @Transactional
    public UserResponse reactivate(Long officerId, Long targetId) {
        guard.requireRole(officerId, Role.OFFICER_ADMIN);
        AppUser target = find(targetId);
        if (target.getStatus() != UserStatus.INACTIVE) {
            throw new BusinessRuleException("Only inactive users can be reactivated");
        }
        target.activate();
        auditWriter.record(officerId, ACTION_REACTIVATE, USER_TABLE, targetId);
        return UserResponse.from(target);
    }

    @Transactional(readOnly = true)
    public UserStatusResponse status(Long userId) {
        return UserStatusResponse.from(find(userId));
    }

    @Transactional(readOnly = true)
    public List<UserNameResponse> names(Collection<Long> ids) {
        if (ids.size() > MAX_BATCH) {
            throw new BusinessRuleException("At most " + MAX_BATCH + " ids per request");
        }
        return users.findAllById(ids).stream().map(UserNameResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<Long> activeIds() {
        return users.findIdsByStatus(UserStatus.ACTIVE);
    }

    private AppUser find(Long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User " + id + " not found"));
    }

    private boolean isLastActiveOfficerAdmin() {
        return users.countByRoleAndStatus(Role.OFFICER_ADMIN, UserStatus.ACTIVE) <= 1;
    }

    /** Lowercase LIKE pattern with %, _ and \ escaped; null when there is no search text. */
    static String likePattern(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String escaped = q.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
