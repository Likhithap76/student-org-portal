package com.studentorgs.portal.task.service;

import static com.studentorgs.portal.task.TestTasks.NOW;
import static com.studentorgs.portal.task.TestTasks.committee;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.studentorgs.portal.common.client.UserDirectoryClient;
import com.studentorgs.portal.common.client.UserDirectoryClient.UserStatusInfo;
import com.studentorgs.portal.common.error.BusinessRuleException;
import com.studentorgs.portal.common.error.ConflictException;
import com.studentorgs.portal.common.error.NotFoundException;
import com.studentorgs.portal.common.security.MemberGuard;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;
import com.studentorgs.portal.task.domain.Committee;
import com.studentorgs.portal.task.domain.CommitteeKind;
import com.studentorgs.portal.task.domain.CommitteeMember;
import com.studentorgs.portal.task.domain.CommitteeMemberId;
import com.studentorgs.portal.task.domain.TaskStatus;
import com.studentorgs.portal.task.dto.AddMembersRequest;
import com.studentorgs.portal.task.dto.CommitteeDetailResponse;
import com.studentorgs.portal.task.dto.CommitteeRequest;
import com.studentorgs.portal.task.dto.CommitteeSummaryResponse;
import com.studentorgs.portal.task.repository.CommitteeMemberRepository;
import com.studentorgs.portal.task.repository.CommitteeRepository;
import com.studentorgs.portal.task.repository.TaskRepository;

@ExtendWith(MockitoExtension.class)
class CommitteeServiceTest {

    private static final UserStatusInfo ACTIVE = new UserStatusInfo(UserStatus.ACTIVE, Role.MEMBER);

    @Mock
    private CommitteeRepository committees;
    @Mock
    private CommitteeMemberRepository members;
    @Mock
    private TaskRepository tasks;
    @Mock
    private MemberGuard guard;
    @Mock
    private UserDirectoryClient users;

    private CommitteeService service;

    @BeforeEach
    void setUp() {
        service = new CommitteeService(committees, members, tasks, guard, users, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void create_newName_savesTrimmed() {
        when(committees.existsByNameIgnoreCase("Events")).thenReturn(false);
        when(committees.save(any(Committee.class))).thenAnswer(inv -> inv.getArgument(0));

        CommitteeSummaryResponse response = service.create(1L, new CommitteeRequest(" Events ", CommitteeKind.COMMITTEE, " "));

        assertThat(response.name()).isEqualTo("Events");
        assertThat(response.description()).isNull();
        assertThat(response.memberCount()).isZero();
        ArgumentCaptor<Committee> saved = ArgumentCaptor.forClass(Committee.class);
        verify(committees).save(saved.capture());
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
        verify(guard).requireRole(1L, Role.OFFICER_ADMIN);
    }

    @Test
    void create_duplicateNameAnyCase_throwsConflict() {
        when(committees.existsByNameIgnoreCase("events")).thenReturn(true);

        assertThatThrownBy(() -> service.create(1L, new CommitteeRequest("events", CommitteeKind.PROJECT, null)))
                .isInstanceOf(ConflictException.class);
        verify(committees, never()).save(any());
    }

    @Test
    void list_page_includesMemberCounts() {
        when(committees.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(committee(5), committee(6)), PageRequest.of(0, 20), 2));
        when(members.countByCommittees(List.of(5L, 6L))).thenReturn(List.of(count(5L, 3)));

        var page = service.list(2L, null, null);

        assertThat(page.content()).extracting(CommitteeSummaryResponse::memberCount).containsExactly(3L, 0L);
        verify(guard).requireActive(2L);
    }

    @Test
    void get_unknown_throwsNotFound() {
        when(committees.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(2L, 9L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void addMembers_activeUsers_savesOnlyNewOnes() {
        when(committees.findById(5L)).thenReturn(Optional.of(committee(5)));
        when(users.status(2L)).thenReturn(Optional.of(ACTIVE));
        when(users.status(3L)).thenReturn(Optional.of(ACTIVE));
        when(members.findByIdCommitteeIdAndIdUserIdIn(5L, List.of(2L, 3L)))
                .thenReturn(List.of(new CommitteeMember(5L, 2L, NOW)));
        when(members.findByIdCommitteeIdOrderByAddedAtAscIdUserIdAsc(5L))
                .thenReturn(List.of(new CommitteeMember(5L, 2L, NOW), new CommitteeMember(5L, 3L, NOW)));
        when(users.names(anyList())).thenReturn(Map.of(2L, "Ana", 3L, "Bob"));

        CommitteeDetailResponse detail = service.addMembers(1L, 5L, new AddMembersRequest(List.of(2L, 3L, 3L)));

        ArgumentCaptor<CommitteeMember> saved = ArgumentCaptor.forClass(CommitteeMember.class);
        verify(members, times(1)).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(new CommitteeMemberId(5L, 3L));
        assertThat(saved.getValue().getAddedAt()).isEqualTo(NOW);
        assertThat(detail.members()).extracting("displayName").containsExactly("Ana", "Bob");
    }

    @Test
    void addMembers_pendingOrUnknownUser_throwsBusinessRuleListingIds() {
        when(committees.findById(5L)).thenReturn(Optional.of(committee(5)));
        when(users.status(2L)).thenReturn(Optional.of(ACTIVE));
        when(users.status(3L)).thenReturn(Optional.of(new UserStatusInfo(UserStatus.PENDING, Role.MEMBER)));
        when(users.status(4L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addMembers(1L, 5L, new AddMembersRequest(List.of(2L, 3L, 4L))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("[3, 4]");
        verify(members, never()).save(any());
    }

    @Test
    void removeMember_noOpenTasks_deletes() {
        CommitteeMember member = new CommitteeMember(5L, 2L, NOW);
        when(committees.findById(5L)).thenReturn(Optional.of(committee(5)));
        when(members.findById(new CommitteeMemberId(5L, 2L))).thenReturn(Optional.of(member));
        when(tasks.existsByCommitteeIdAndAssigneeIdAndStatusNot(5L, 2L, TaskStatus.DONE)).thenReturn(false);

        service.removeMember(1L, 5L, 2L);

        verify(members).delete(member);
    }

    @Test
    void removeMember_hasOpenTasks_throwsBusinessRule() {
        when(committees.findById(5L)).thenReturn(Optional.of(committee(5)));
        when(members.findById(new CommitteeMemberId(5L, 2L))).thenReturn(Optional.of(new CommitteeMember(5L, 2L, NOW)));
        when(tasks.existsByCommitteeIdAndAssigneeIdAndStatusNot(5L, 2L, TaskStatus.DONE)).thenReturn(true);

        assertThatThrownBy(() -> service.removeMember(1L, 5L, 2L)).isInstanceOf(BusinessRuleException.class);
        verify(members, never()).delete(any());
    }

    @Test
    void removeMember_notAMember_throwsNotFound() {
        when(committees.findById(5L)).thenReturn(Optional.of(committee(5)));
        when(members.findById(new CommitteeMemberId(5L, 2L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeMember(1L, 5L, 2L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void memberNames_committee_returnsIdsWithNamesInJoinOrder() {
        when(committees.findById(5L)).thenReturn(Optional.of(committee(5)));
        when(members.findByIdCommitteeIdOrderByAddedAtAscIdUserIdAsc(5L))
                .thenReturn(List.of(new CommitteeMember(5L, 3L, NOW), new CommitteeMember(5L, 2L, NOW)));
        when(users.names(List.of(3L, 2L))).thenReturn(Map.of(2L, "Ana", 3L, "Bob"));

        assertThat(service.memberNames(5L)).extracting("userId", "displayName")
                .containsExactly(org.assertj.core.groups.Tuple.tuple(3L, "Bob"), org.assertj.core.groups.Tuple.tuple(2L, "Ana"));
    }

    @Test
    void memberNames_unknownCommittee_throwsNotFound() {
        when(committees.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.memberNames(9L)).isInstanceOf(NotFoundException.class);
    }

    private static CommitteeMemberRepository.CommitteeCount count(Long committeeId, long total) {
        return new CommitteeMemberRepository.CommitteeCount() {
            @Override
            public Long getCommitteeId() {
                return committeeId;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
