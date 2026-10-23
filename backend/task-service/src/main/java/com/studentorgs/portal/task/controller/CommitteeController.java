package com.studentorgs.portal.task.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.studentorgs.portal.common.paging.PageResponse;
import com.studentorgs.portal.common.security.CurrentUser;
import com.studentorgs.portal.task.dto.AddMembersRequest;
import com.studentorgs.portal.task.dto.CommitteeDetailResponse;
import com.studentorgs.portal.task.dto.CommitteeRequest;
import com.studentorgs.portal.task.dto.CommitteeSummaryResponse;
import com.studentorgs.portal.task.service.CommitteeService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/committees")
@RequiredArgsConstructor
public class CommitteeController {

    private final CommitteeService committeeService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OFFICER_ADMIN')")
    @Operation(summary = "Create a committee or project (OFFICER_ADMIN). Duplicate name → 409")
    public CommitteeSummaryResponse create(@Valid @RequestBody CommitteeRequest request) {
        return committeeService.create(CurrentUser.id(), request);
    }

    @GetMapping
    @Operation(summary = "Committees and projects with member counts (active members)")
    public PageResponse<CommitteeSummaryResponse> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return committeeService.list(CurrentUser.id(), page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Committee detail with members (active members)")
    public CommitteeDetailResponse get(@PathVariable Long id) {
        return committeeService.get(CurrentUser.id(), id);
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasRole('OFFICER_ADMIN')")
    @Operation(summary = "Add active users to a committee (OFFICER_ADMIN). Existing members are skipped")
    public CommitteeDetailResponse addMembers(@PathVariable Long id, @Valid @RequestBody AddMembersRequest request) {
        return committeeService.addMembers(CurrentUser.id(), id, request);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('OFFICER_ADMIN')")
    @Operation(summary = "Remove a member (OFFICER_ADMIN). 422 if they still have open tasks here")
    public void removeMember(@PathVariable Long id, @PathVariable Long userId) {
        committeeService.removeMember(CurrentUser.id(), id, userId);
    }
}
