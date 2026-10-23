package com.studentorgs.portal.user.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.studentorgs.portal.common.paging.PageResponse;
import com.studentorgs.portal.common.security.CurrentUser;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.user.dto.ChangeRoleRequest;
import com.studentorgs.portal.user.dto.RosterEntryResponse;
import com.studentorgs.portal.user.dto.UserResponse;
import com.studentorgs.portal.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/roster")
    @PreAuthorize("hasRole('OFFICER_ADMIN')")
    @Operation(summary = "Active members with email and role (OFFICER_ADMIN)")
    public PageResponse<RosterEntryResponse> roster(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return userService.roster(CurrentUser.id(), q, role, page, size);
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('OFFICER_ADMIN')")
    @Operation(summary = "Change a user's role, member ↔ officer_admin (OFFICER_ADMIN). The last officer_admin cannot be demoted")
    public UserResponse changeRole(@PathVariable Long id, @Valid @RequestBody ChangeRoleRequest request) {
        return userService.changeRole(CurrentUser.id(), id, request.role());
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('OFFICER_ADMIN')")
    @Operation(summary = "Deactivate an active user (OFFICER_ADMIN). Not yourself, not the last officer_admin")
    public UserResponse deactivate(@PathVariable Long id) {
        return userService.deactivate(CurrentUser.id(), id);
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('OFFICER_ADMIN')")
    @Operation(summary = "Reactivate an inactive user (OFFICER_ADMIN)")
    public UserResponse reactivate(@PathVariable Long id) {
        return userService.reactivate(CurrentUser.id(), id);
    }
}
