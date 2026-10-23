package com.studentorgs.portal.task.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.studentorgs.portal.common.paging.PageResponse;
import com.studentorgs.portal.common.security.CurrentUser;
import com.studentorgs.portal.task.domain.TaskStatus;
import com.studentorgs.portal.task.dto.TaskBatchRequest;
import com.studentorgs.portal.task.dto.TaskRequest;
import com.studentorgs.portal.task.dto.TaskResponse;
import com.studentorgs.portal.task.dto.TaskStatusRequest;
import com.studentorgs.portal.task.dto.TaskUpdateRequest;
import com.studentorgs.portal.task.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OFFICER_ADMIN')")
    @Operation(summary = "Create and assign a task (OFFICER_ADMIN). The assignee must be a committee member")
    public TaskResponse create(@Valid @RequestBody TaskRequest request) {
        return taskService.create(CurrentUser.id(), request);
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OFFICER_ADMIN')")
    @Operation(summary = "Create up to 20 tasks in one committee, all or nothing (OFFICER_ADMIN)")
    public List<TaskResponse> createBatch(@Valid @RequestBody TaskBatchRequest request) {
        return taskService.createBatch(CurrentUser.id(), request);
    }

    @GetMapping
    @Operation(summary = "Tasks filtered by committee, assignee and status, by due date (active members)")
    public PageResponse<TaskResponse> list(
            @RequestParam(required = false) Long committeeId,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return taskService.list(CurrentUser.id(), committeeId, assigneeId, status, page, size);
    }

    @GetMapping("/me")
    @Operation(summary = "My tasks: overdue first, then open by due date, then done")
    public List<TaskResponse> mine() {
        return taskService.mine(CurrentUser.id());
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Move a task: TODO→IN_PROGRESS→DONE, DONE→IN_PROGRESS (assignee or OFFICER_ADMIN)")
    public TaskResponse changeStatus(@PathVariable Long id, @Valid @RequestBody TaskStatusRequest request) {
        return taskService.changeStatus(CurrentUser.id(), id, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OFFICER_ADMIN')")
    @Operation(summary = "Edit or reassign a task (OFFICER_ADMIN). Same membership rule as create")
    public TaskResponse update(@PathVariable Long id, @Valid @RequestBody TaskUpdateRequest request) {
        return taskService.update(CurrentUser.id(), id, request);
    }
}
