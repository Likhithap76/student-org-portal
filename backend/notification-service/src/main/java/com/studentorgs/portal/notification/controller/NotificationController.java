package com.studentorgs.portal.notification.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.studentorgs.portal.common.paging.PageResponse;
import com.studentorgs.portal.common.security.CurrentUser;
import com.studentorgs.portal.notification.dto.CountResponse;
import com.studentorgs.portal.notification.dto.NotificationResponse;
import com.studentorgs.portal.notification.dto.UpdatedResponse;
import com.studentorgs.portal.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/me")
    @Operation(summary = "My notifications, newest first")
    public PageResponse<NotificationResponse> mine(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return notificationService.mine(CurrentUser.id(), unreadOnly, page, size);
    }

    @GetMapping("/me/unread-count")
    @Operation(summary = "How many of my notifications are unread")
    public CountResponse unreadCount() {
        return new CountResponse(notificationService.unreadCount(CurrentUser.id()));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark one of my notifications as read")
    public NotificationResponse markRead(@PathVariable Long id) {
        return notificationService.markRead(CurrentUser.id(), id);
    }

    @PostMapping("/me/read-all")
    @Operation(summary = "Mark all my notifications as read")
    public UpdatedResponse readAll() {
        return new UpdatedResponse(notificationService.markAllRead(CurrentUser.id()));
    }
}
