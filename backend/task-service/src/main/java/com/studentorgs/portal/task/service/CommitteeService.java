package com.studentorgs.portal.task.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studentorgs.portal.common.client.UserDirectoryClient;
import com.studentorgs.portal.common.client.UserDirectoryClient.UserStatusInfo;
import com.studentorgs.portal.common.error.BusinessRuleException;
import com.studentorgs.portal.common.error.ConflictException;
import com.studentorgs.portal.common.error.NotFoundException;
import com.studentorgs.portal.common.paging.PageRequests;
import com.studentorgs.portal.common.paging.PageResponse;
import com.studentorgs.portal.common.security.MemberGuard;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;
import com.studentorgs.portal.task.domain.Committee;
import com.studentorgs.portal.task.domain.CommitteeMember;
import com.studentorgs.portal.task.domain.CommitteeMemberId;
import com.studentorgs.portal.task.domain.TaskStatus;
import com.studentorgs.portal.task.dto.AddMembersRequest;
import com.studentorgs.portal.task.dto.CommitteeDetailResponse;
import com.studentorgs.portal.task.dto.CommitteeMemberResponse;
import com.studentorgs.portal.task.dto.CommitteeRequest;
import com.studentorgs.portal.task.dto.CommitteeSummaryResponse;
import com.studentorgs.portal.task.dto.MemberNameResponse;
import com.studentorgs.portal.task.repository.CommitteeMemberRepository;
import com.studentorgs.portal.task.repository.CommitteeRepository;
import com.studentorgs.portal.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;

/** Committees / projects and their members. */
@Service
@RequiredArgsConstructor
public class CommitteeService {

    private static final Sort BY_NAME = Sort.by("name").ascending();

    private final CommitteeRepository committees;
    private final CommitteeMemberRepository members;
    private final TaskRepository tasks;
    private final MemberGuard guard;
    private final UserDirectoryClient users;
    private final Clock clock;

    @Transactional
    public CommitteeSummaryResponse create(Long officerId, CommitteeRequest request) {
        guard.requireRole(officerId, Role.OFFICER_ADMIN);
        String name = request.name().trim();
        if (committees.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A committee named '" + name + "' already exists");
        }
        String description = request.description() == null || request.description().isBlank()
                ? null : request.description().trim();
        Committee committee = committees.save(new Committee(name, request.kind(), description, clock.instant()));
        return CommitteeSummaryResponse.from(committee, 0);
    }

    @Transactional(readOnly = true)
    public PageResponse<CommitteeSummaryResponse> list(Long userId, Integer page, Integer size) {
        guard.requireActive(userId);
        Page<Committee> result = committees.findAll(PageRequests.of(page, size, BY_NAME));
        List<Long> ids = result.getContent().stream().map(Committee::getId).toList();
        Map<Long, Long> counts = ids.isEmpty() ? Map.of() : members.countByCommittees(ids).stream()
                .collect(Collectors.toMap(CommitteeMemberRepository.CommitteeCount::getCommitteeId,
                        CommitteeMemberRepository.CommitteeCount::getTotal));
        return PageResponse.from(result, c -> CommitteeSummaryResponse.from(c, counts.getOrDefault(c.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public CommitteeDetailResponse get(Long userId, Long committeeId) {
        guard.requireActive(userId);
        return detail(find(committeeId));
    }

    /** Adds active users; users who are already members are skipped. */
    @Transactional
    public CommitteeDetailResponse addMembers(Long officerId, Long committeeId, AddMembersRequest request) {
        guard.requireRole(officerId, Role.OFFICER_ADMIN);
        Committee committee = find(committeeId);
        List<Long> userIds = request.userIds().stream().distinct().toList();
        List<Long> notActive = userIds.stream()
                .filter(id -> users.status(id).map(UserStatusInfo::status).orElse(null) != UserStatus.ACTIVE)
                .toList();
        if (!notActive.isEmpty()) {
            throw new BusinessRuleException("Only active members can be added. Not active or unknown: " + notActive);
        }
        Set<Long> already = members.findByIdCommitteeIdAndIdUserIdIn(committeeId, userIds).stream()
                .map(m -> m.getId().getUserId()).collect(Collectors.toSet());
        Instant now = clock.instant();
        userIds.stream().filter(id -> !already.contains(id))
                .forEach(id -> members.save(new CommitteeMember(committeeId, id, now)));
        members.flush();
        return detail(committee);
    }

    /** Removes a member unless they still have open tasks in this committee. */
    @Transactional
    public void removeMember(Long officerId, Long committeeId, Long userId) {
        guard.requireRole(officerId, Role.OFFICER_ADMIN);
        find(committeeId);
        CommitteeMember member = members.findById(new CommitteeMemberId(committeeId, userId))
                .orElseThrow(() -> new NotFoundException("User " + userId + " is not a member of this committee"));
        if (tasks.existsByCommitteeIdAndAssigneeIdAndStatusNot(committeeId, userId, TaskStatus.DONE)) {
            throw new BusinessRuleException("This member still has open tasks in the committee. Reassign or finish them first");
        }
        members.delete(member);
    }

    /** Internal: members with display names, for other services (no caller check; internal token only). */
    @Transactional(readOnly = true)
    public List<MemberNameResponse> memberNames(Long committeeId) {
        find(committeeId);
        List<Long> ids = members.findByIdCommitteeIdOrderByAddedAtAscIdUserIdAsc(committeeId).stream()
                .map(m -> m.getId().getUserId()).toList();
        Map<Long, String> names = users.names(ids);
        return ids.stream().map(id -> new MemberNameResponse(id, names.get(id))).toList();
    }

    Committee find(Long committeeId) {
        return committees.findById(committeeId)
                .orElseThrow(() -> new NotFoundException("Committee " + committeeId + " not found"));
    }

    boolean isMember(Long committeeId, Long userId) {
        return members.existsById(new CommitteeMemberId(committeeId, userId));
    }

    private CommitteeDetailResponse detail(Committee committee) {
        List<CommitteeMember> rows = members.findByIdCommitteeIdOrderByAddedAtAscIdUserIdAsc(committee.getId());
        Map<Long, String> names = users.names(rows.stream().map(m -> m.getId().getUserId()).toList());
        return CommitteeDetailResponse.from(committee, rows.stream()
                .map(m -> CommitteeMemberResponse.from(m, names.get(m.getId().getUserId())))
                .toList());
    }
}
