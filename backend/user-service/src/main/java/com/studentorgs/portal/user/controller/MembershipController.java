package com.studentorgs.portal.user.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.studentorgs.portal.common.paging.PageResponse;
import com.studentorgs.portal.common.security.CurrentUser;
import com.studentorgs.portal.user.domain.Decision;
import com.studentorgs.portal.user.dto.ApplicationResponse;
import com.studentorgs.portal.user.service.MembershipService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/memberships")
@RequiredArgsConstructor
public class MembershipController {

    private final MembershipService membershipService;

    @GetMapping("/me")
    @Operation(summary = "My latest membership application")
    public ApplicationResponse mine() {
        return membershipService.myLatest(CurrentUser.id());
    }

    @PostMapping("/reapply")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Apply again after a rejection (409 if an application is already pending)")
    public ApplicationResponse reapply() {
        return membershipService.reapply(CurrentUser.id());
    }

    @GetMapping
    @PreAuthorize("hasRole('OFFICER_ADMIN')")
    @Operation(summary = "Review queue, oldest first (OFFICER_ADMIN)")
    public PageResponse<ApplicationResponse> queue(
            @RequestParam(defaultValue = "PENDING") Decision decision,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return membershipService.queue(CurrentUser.id(), decision, page, size);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('OFFICER_ADMIN')")
    @Operation(summary = "Approve an application and activate the member (OFFICER_ADMIN)")
    public ApplicationResponse approve(@PathVariable Long id) {
        return membershipService.approve(CurrentUser.id(), id);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('OFFICER_ADMIN')")
    @Operation(summary = "Reject an application; the user stays pending (OFFICER_ADMIN)")
    public ApplicationResponse reject(@PathVariable Long id) {
        return membershipService.reject(CurrentUser.id(), id);
    }
}
