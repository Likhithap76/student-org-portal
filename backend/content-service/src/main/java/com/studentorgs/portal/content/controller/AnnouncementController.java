package com.studentorgs.portal.content.controller;

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
import com.studentorgs.portal.content.dto.AnnouncementRequest;
import com.studentorgs.portal.content.dto.AnnouncementResponse;
import com.studentorgs.portal.content.service.AnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OFFICER_ADMIN')")
    @Operation(summary = "Publish an announcement (OFFICER_ADMIN); all active users are notified")
    public AnnouncementResponse publish(@Valid @RequestBody AnnouncementRequest request) {
        return announcementService.publish(CurrentUser.id(), request);
    }

    @GetMapping
    @Operation(summary = "Announcements, newest first (active members)")
    public PageResponse<AnnouncementResponse> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return announcementService.list(CurrentUser.id(), page, size);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an announcement (publisher or OFFICER_ADMIN)")
    public void delete(@PathVariable Long id) {
        announcementService.delete(CurrentUser.id(), id);
    }
}
