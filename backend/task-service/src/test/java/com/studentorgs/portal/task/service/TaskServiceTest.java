package com.studentorgs.portal.task.service;

import static com.studentorgs.portal.task.TestTasks.NOW;
import static com.studentorgs.portal.task.TestTasks.TODAY;
import static com.studentorgs.portal.task.TestTasks.committee;
import static com.studentorgs.portal.task.TestTasks.task;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.studentorgs.portal.common.client.UserDirectoryClient;
import com.studentorgs.portal.common.client.UserDirectoryClient.UserStatusInfo;
import com.studentorgs.portal.common.config.OrgPortalProperties;
import com.studentorgs.portal.common.error.BusinessRuleException;
import com.studentorgs.portal.common.error.ForbiddenOperationException;
import com.studentorgs.portal.common.error.NotFoundException;
import com.studentorgs.portal.common.security.MemberGuard;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;
import com.studentorgs.portal.task.domain.Task;
import com.studentorgs.portal.task.domain.TaskStatus;
import com.studentorgs.portal.task.dto.TaskBatchItem;
import com.studentorgs.portal.task.dto.TaskBatchRequest;
import com.studentorgs.portal.task.dto.TaskRequest;
import com.studentorgs.portal.task.dto.TaskResponse;
import com.studentorgs.portal.task.dto.TaskStatusRequest;
import com.studentorgs.portal.task.dto.TaskUpdateRequest;
import com.studentorgs.portal.task.repository.TaskRepository;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    private static final long OFFICER_ADMIN_ID = 1L;
    private static final long ANA = 2L;
    private static final long BOB = 3L;
    private static final UserStatusInfo ACTIVE_MEMBER = new UserStatusInfo(UserStatus.ACTIVE, Role.MEMBER);

    @Mock
    private TaskRepository tasks;
    @Mock
    private CommitteeService committees;
    @Mock
    private MemberGuard guard;
    @Mock
    private UserDirectoryClient users;
    @Mock
    private ApplicationEventPublisher events;

    private TaskService service;

    @BeforeEach
    void setUp() {
        OrgPortalProperties properties = new OrgPortalProperties(null, null, null, ZoneId.of("America/Chicago"));
        service = new TaskService(tasks, committees, guard, users, events, Clock.fixed(NOW, ZoneOffset.UTC), properties);
    }

    private void assignable(long committeeId, long userId) {
        when(committees.isMember(committeeId, userId)).thenReturn(true);
        when(users.status(userId)).thenReturn(Optional.of(ACTIVE_MEMBER));
    }

    // --- create ---

    @Test
    void create_memberAssignee_savesTodoAndNotifies() {
        when(committees.find(5L)).thenReturn(committee(5));
        assignable(5L, ANA);
        when(tasks.save(any(Task.class))).thenAnswer(inv -> {
            Task t = inv.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(t, "id", 40L);
            return t;
        });

        TaskResponse response = service.create(OFFICER_ADMIN_ID, new TaskRequest(5L, " Book room ", ANA, TODAY.plusDays(3)));

        assertThat(response.status()).isEqualTo(TaskStatus.TODO);
        assertThat(response.title()).isEqualTo("Book room");
        assertThat(response.createdBy()).isEqualTo(OFFICER_ADMIN_ID);
        assertThat(response.overdue()).isFalse();
        verify(guard).requireRole(OFFICER_ADMIN_ID, Role.OFFICER_ADMIN);
        verify(events).publishEvent(new TaskAssignedEvent(40L, ANA, "Book room"));
    }

    @Test
    void create_assigneeNotMember_throwsBusinessRule() {
        when(committees.find(5L)).thenReturn(committee(5));
        when(committees.isMember(5L, ANA)).thenReturn(false);

        assertThatThrownBy(() -> service.create(OFFICER_ADMIN_ID, new TaskRequest(5L, "t", ANA, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("member of the committee");
        verify(tasks, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void create_assigneeDeactivated_throwsBusinessRule() {
        when(committees.find(5L)).thenReturn(committee(5));
        when(committees.isMember(5L, ANA)).thenReturn(true);
        when(users.status(ANA)).thenReturn(Optional.of(new UserStatusInfo(UserStatus.INACTIVE, Role.MEMBER)));

        assertThatThrownBy(() -> service.create(OFFICER_ADMIN_ID, new TaskRequest(5L, "t", ANA, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("active");
    }

    @Test
    void create_unknownCommittee_throwsNotFound() {
        when(committees.find(9L)).thenThrow(new NotFoundException("Committee 9 not found"));

        assertThatThrownBy(() -> service.create(OFFICER_ADMIN_ID, new TaskRequest(9L, "t", ANA, null)))
                .isInstanceOf(NotFoundException.class);
    }

    // --- createBatch ---

    @Test
    void createBatch_allAssigneesMembers_savesEachAndNotifies() {
        when(committees.find(5L)).thenReturn(committee(5));
        assignable(5L, ANA);
        assignable(5L, BOB);
        when(tasks.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        List<TaskResponse> created = service.createBatch(OFFICER_ADMIN_ID, new TaskBatchRequest(5L, List.of(
                new TaskBatchItem(" Book room ", ANA, TODAY.plusDays(2)),
                new TaskBatchItem("Order food", BOB, null),
                new TaskBatchItem("Send agenda", ANA, null))));

        assertThat(created).extracting(TaskResponse::title).containsExactly("Book room", "Order food", "Send agenda");
        assertThat(created).extracting(TaskResponse::status).containsOnly(TaskStatus.TODO);
        verify(tasks, org.mockito.Mockito.times(3)).save(any(Task.class));
        verify(events, org.mockito.Mockito.times(3)).publishEvent(any(TaskAssignedEvent.class));
        // ANA's membership is checked once even though she has two items.
        verify(committees, org.mockito.Mockito.times(1)).isMember(5L, ANA);
    }

    @Test
    void createBatch_oneAssigneeNotMember_savesNothing() {
        when(committees.find(5L)).thenReturn(committee(5));
        assignable(5L, ANA);
        when(committees.isMember(5L, BOB)).thenReturn(false);

        assertThatThrownBy(() -> service.createBatch(OFFICER_ADMIN_ID, new TaskBatchRequest(5L, List.of(
                new TaskBatchItem("a", ANA, null), new TaskBatchItem("b", BOB, null)))))
                .isInstanceOf(BusinessRuleException.class);
        verify(tasks, never()).save(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void createBatch_notOfficer_throwsForbidden() {
        when(guard.requireRole(ANA, Role.OFFICER_ADMIN)).thenThrow(new ForbiddenOperationException("no"));

        assertThatThrownBy(() -> service.createBatch(ANA, new TaskBatchRequest(5L, List.of(new TaskBatchItem("a", ANA, null)))))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    // --- update ---

    @Test
    void update_reassignToMember_changesAndNotifiesNewAssignee() {
        Task task = task(40, 5, ANA, null);
        when(tasks.findById(40L)).thenReturn(Optional.of(task));
        assignable(5L, BOB);

        TaskResponse response = service.update(OFFICER_ADMIN_ID, 40L, new TaskUpdateRequest("Renamed", BOB, TODAY));

        assertThat(response.assigneeId()).isEqualTo(BOB);
        assertThat(response.dueDate()).isEqualTo(TODAY);
        verify(events).publishEvent(new TaskAssignedEvent(40L, BOB, "Renamed"));
    }

    @Test
    void update_reassignToNonMember_throwsBusinessRule() {
        when(tasks.findById(40L)).thenReturn(Optional.of(task(40, 5, ANA, null)));
        when(committees.isMember(5L, BOB)).thenReturn(false);

        assertThatThrownBy(() -> service.update(OFFICER_ADMIN_ID, 40L, new TaskUpdateRequest("t", BOB, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void update_sameAssignee_noMembershipCheckNoNotification() {
        when(tasks.findById(40L)).thenReturn(Optional.of(task(40, 5, ANA, null)));

        service.update(OFFICER_ADMIN_ID, 40L, new TaskUpdateRequest("New title", ANA, null));

        verify(committees, never()).isMember(any(), any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void update_unknownTask_throwsNotFound() {
        when(tasks.findById(40L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(OFFICER_ADMIN_ID, 40L, new TaskUpdateRequest("t", ANA, null)))
                .isInstanceOf(NotFoundException.class);
    }

    // --- changeStatus ---

    @Test
    void changeStatus_assignee_movesTask() {
        when(guard.requireActive(ANA)).thenReturn(ACTIVE_MEMBER);
        when(tasks.findById(40L)).thenReturn(Optional.of(task(40, 5, ANA, null)));

        assertThat(service.changeStatus(ANA, 40L, new TaskStatusRequest(TaskStatus.IN_PROGRESS)).status())
                .isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void changeStatus_officerNotAssignee_movesTask() {
        when(guard.requireActive(OFFICER_ADMIN_ID)).thenReturn(new UserStatusInfo(UserStatus.ACTIVE, Role.OFFICER_ADMIN));
        when(tasks.findById(40L)).thenReturn(Optional.of(task(40, 5, ANA, null)));

        assertThat(service.changeStatus(OFFICER_ADMIN_ID, 40L, new TaskStatusRequest(TaskStatus.IN_PROGRESS)).status())
                .isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void changeStatus_otherMember_throwsForbidden() {
        when(guard.requireActive(BOB)).thenReturn(ACTIVE_MEMBER);
        when(tasks.findById(40L)).thenReturn(Optional.of(task(40, 5, ANA, null)));

        assertThatThrownBy(() -> service.changeStatus(BOB, 40L, new TaskStatusRequest(TaskStatus.IN_PROGRESS)))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void changeStatus_doneThenReopen_setsThenClearsCompletedAt() {
        Task task = task(40, 5, ANA, null);
        when(guard.requireActive(ANA)).thenReturn(ACTIVE_MEMBER);
        when(tasks.findById(40L)).thenReturn(Optional.of(task));

        service.changeStatus(ANA, 40L, new TaskStatusRequest(TaskStatus.IN_PROGRESS));
        TaskResponse done = service.changeStatus(ANA, 40L, new TaskStatusRequest(TaskStatus.DONE));
        assertThat(done.completedAt()).isEqualTo(NOW);

        TaskResponse reopened = service.changeStatus(ANA, 40L, new TaskStatusRequest(TaskStatus.IN_PROGRESS));
        assertThat(reopened.completedAt()).isNull();
    }

    @Test
    void changeStatus_skipToDone_throwsBusinessRule() {
        when(guard.requireActive(ANA)).thenReturn(ACTIVE_MEMBER);
        when(tasks.findById(40L)).thenReturn(Optional.of(task(40, 5, ANA, null)));

        assertThatThrownBy(() -> service.changeStatus(ANA, 40L, new TaskStatusRequest(TaskStatus.DONE)))
                .isInstanceOf(BusinessRuleException.class);
    }

    // --- list / mine ---

    @Test
    @SuppressWarnings("unchecked")
    void list_filters_returnsPageWithOverdueFlag() {
        when(tasks.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(task(40, 5, ANA, TODAY.minusDays(1))), PageRequest.of(0, 20), 1));

        var page = service.list(ANA, 5L, ANA, TaskStatus.TODO, null, null);

        assertThat(page.content().get(0).overdue()).isTrue();
        verify(guard).requireActive(ANA);
    }

    @Test
    void mine_mixedTasks_overdueFirstThenOpenByDueThenDone() {
        Task done = task(1, 5, ANA, TODAY.minusDays(10));
        done.changeStatus(TaskStatus.IN_PROGRESS, NOW);
        done.changeStatus(TaskStatus.DONE, NOW);
        Task noDue = task(2, 5, ANA, null);
        Task dueLater = task(3, 5, ANA, TODAY.plusDays(5));
        Task overdue = task(4, 5, ANA, TODAY.minusDays(2));
        Task dueToday = task(5, 5, ANA, TODAY);
        when(tasks.findByAssigneeId(ANA)).thenReturn(List.of(done, noDue, dueLater, overdue, dueToday));

        List<TaskResponse> mine = service.mine(ANA);

        assertThat(mine).extracting(TaskResponse::id).containsExactly(4L, 5L, 3L, 2L, 1L);
        assertThat(mine.get(0).overdue()).isTrue();
    }

    @Test
    void today_usesOrganizationTimeZone() {
        // 03:00 UTC on Oct 21 is still Oct 20 in Chicago, so a task due Oct 20 is not overdue yet.
        Instant lateEvening = Instant.parse("2026-10-21T03:00:00Z");
        OrgPortalProperties properties = new OrgPortalProperties(null, null, null, ZoneId.of("America/Chicago"));
        TaskService chicago = new TaskService(tasks, committees, guard, users, events,
                Clock.fixed(lateEvening, ZoneOffset.UTC), properties);
        when(tasks.findByAssigneeId(ANA)).thenReturn(List.of(task(9, 5, ANA, TODAY)));

        assertThat(chicago.mine(ANA).get(0).overdue()).isFalse();
    }
}
