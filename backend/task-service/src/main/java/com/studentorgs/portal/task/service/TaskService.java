package com.studentorgs.portal.task.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studentorgs.portal.common.client.UserDirectoryClient;
import com.studentorgs.portal.common.client.UserDirectoryClient.UserStatusInfo;
import com.studentorgs.portal.common.config.OrgPortalProperties;
import com.studentorgs.portal.common.error.BusinessRuleException;
import com.studentorgs.portal.common.error.ForbiddenOperationException;
import com.studentorgs.portal.common.error.NotFoundException;
import com.studentorgs.portal.common.paging.PageRequests;
import com.studentorgs.portal.common.paging.PageResponse;
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

/** Task assignment, editing, status changes and listing. */
@Service
public class TaskService {

    private static final Sort BY_DUE_DATE = Sort.by(Sort.Order.asc("dueDate").nullsLast(), Sort.Order.asc("id"));

    /** /tasks/me order: overdue first, then open by due date (none last), then done (latest first). */
    static Comparator<Task> myTasksOrder(LocalDate today) {
        Comparator<Task> group = Comparator.comparingInt(t -> t.isOverdue(today) ? 0 : t.isOpen() ? 1 : 2);
        return group
                .thenComparing(Task::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Task::getCompletedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Task::getId);
    }

    private final TaskRepository tasks;
    private final CommitteeService committees;
    private final MemberGuard guard;
    private final UserDirectoryClient users;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final ZoneId zone;

    public TaskService(TaskRepository tasks, CommitteeService committees, MemberGuard guard, UserDirectoryClient users,
            ApplicationEventPublisher events, Clock clock, OrgPortalProperties properties) {
        this.tasks = tasks;
        this.committees = committees;
        this.guard = guard;
        this.users = users;
        this.events = events;
        this.clock = clock;
        this.zone = properties.timeZone();
    }

    @Transactional
    public TaskResponse create(Long officerId, TaskRequest request) {
        guard.requireRole(officerId, Role.OFFICER_ADMIN);
        committees.find(request.committeeId());
        requireAssignable(request.committeeId(), request.assigneeId());
        String title = request.title().trim();
        Task task = tasks.save(Task.assign(request.committeeId(), title, request.assigneeId(), request.dueDate(),
                officerId, clock.instant()));
        events.publishEvent(new TaskAssignedEvent(task.getId(), task.getAssigneeId(), title));
        return TaskResponse.from(task, today());
    }

    /**
     * Creates several tasks in one committee, all or nothing: if any assignee is not an active committee
     * member, nothing is saved. Each assignee is notified after commit.
     */
    @Transactional
    public List<TaskResponse> createBatch(Long officerId, TaskBatchRequest request) {
        guard.requireRole(officerId, Role.OFFICER_ADMIN);
        committees.find(request.committeeId());
        Map<Long, Boolean> checked = new HashMap<>();
        for (TaskBatchItem item : request.items()) {
            if (checked.putIfAbsent(item.assigneeId(), Boolean.TRUE) == null) {
                requireAssignable(request.committeeId(), item.assigneeId());
            }
        }
        LocalDate today = today();
        return request.items().stream().map(item -> {
            String title = item.title().trim();
            Task task = tasks.save(Task.assign(request.committeeId(), title, item.assigneeId(), item.dueDate(),
                    officerId, clock.instant()));
            events.publishEvent(new TaskAssignedEvent(task.getId(), task.getAssigneeId(), title));
            return TaskResponse.from(task, today);
        }).toList();
    }

    /** Edit title / due date, or reassign (the new assignee must be an active committee member). */
    @Transactional
    public TaskResponse update(Long officerId, Long taskId, TaskUpdateRequest request) {
        guard.requireRole(officerId, Role.OFFICER_ADMIN);
        Task task = find(taskId);
        boolean reassigned = !task.getAssigneeId().equals(request.assigneeId());
        if (reassigned) {
            requireAssignable(task.getCommitteeId(), request.assigneeId());
        }
        task.edit(request.title().trim(), request.assigneeId(), request.dueDate(), clock.instant());
        if (reassigned) {
            events.publishEvent(new TaskAssignedEvent(task.getId(), task.getAssigneeId(), task.getTitle()));
        }
        return TaskResponse.from(task, today());
    }

    /** The assignee or an officer_admin may move a task; see {@link Task#changeStatus}. */
    @Transactional
    public TaskResponse changeStatus(Long callerId, Long taskId, TaskStatusRequest request) {
        UserStatusInfo caller = guard.requireActive(callerId);
        Task task = find(taskId);
        if (!task.getAssigneeId().equals(callerId) && !caller.role().includes(Role.OFFICER_ADMIN)) {
            throw new ForbiddenOperationException("Only the assignee or an officer_admin can change this task's status");
        }
        task.changeStatus(request.status(), clock.instant());
        return TaskResponse.from(task, today());
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> list(Long userId, Long committeeId, Long assigneeId, TaskStatus status,
            Integer page, Integer size) {
        guard.requireActive(userId);
        Specification<Task> filter = Specification.where(TaskRepository.hasValue("committeeId", committeeId))
                .and(TaskRepository.hasValue("assigneeId", assigneeId))
                .and(TaskRepository.hasValue("status", status));
        LocalDate today = today();
        return PageResponse.from(tasks.findAll(filter, PageRequests.of(page, size, BY_DUE_DATE)),
                t -> TaskResponse.from(t, today));
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> mine(Long userId) {
        guard.requireActive(userId);
        LocalDate today = today();
        return tasks.findByAssigneeId(userId).stream()
                .sorted(myTasksOrder(today))
                .map(t -> TaskResponse.from(t, today))
                .toList();
    }

    private void requireAssignable(Long committeeId, Long assigneeId) {
        if (!committees.isMember(committeeId, assigneeId)) {
            throw new BusinessRuleException("The assignee must be a member of the committee");
        }
        UserStatus status = users.status(assigneeId).map(UserStatusInfo::status).orElse(null);
        if (status != UserStatus.ACTIVE) {
            throw new BusinessRuleException("The assignee must be an active member");
        }
    }

    private Task find(Long taskId) {
        return tasks.findById(taskId).orElseThrow(() -> new NotFoundException("Task " + taskId + " not found"));
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), zone);
    }
}
