package com.studentorgs.portal.notification.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.studentorgs.portal.notification.dto.CreateNotificationsRequest;
import com.studentorgs.portal.notification.dto.CreatedResponse;
import com.studentorgs.portal.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Called by other services with X-Internal-Token; blocked at the gateway. */
@Hidden
@RestController
@RequestMapping("/internal/notifications")
@RequiredArgsConstructor
public class InternalNotificationController {

    private final NotificationService notificationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedResponse create(@Valid @RequestBody CreateNotificationsRequest request) {
        return new CreatedResponse(notificationService.create(request));
    }
}
