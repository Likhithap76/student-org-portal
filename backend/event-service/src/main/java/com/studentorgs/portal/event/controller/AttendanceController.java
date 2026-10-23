package com.studentorgs.portal.event.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studentorgs.portal.common.security.CurrentUser;
import com.studentorgs.portal.event.dto.AttendanceEntryRequest;
import com.studentorgs.portal.event.dto.AttendanceResponse;
import com.studentorgs.portal.event.service.AttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/events/{eventId}/attendance")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OFFICER_ADMIN')")
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PutMapping
    @Operation(summary = "Record attendance [{userId, status}] (OFFICER_ADMIN, after the event starts). Creates or updates")
    public AttendanceResponse record(@PathVariable Long eventId,
            @RequestBody @Size(min = 1, max = 100) List<@Valid AttendanceEntryRequest> entries) {
        return attendanceService.record(CurrentUser.id(), eventId, entries);
    }

    @GetMapping
    @Operation(summary = "Attendance list and rate = present / going (OFFICER_ADMIN)")
    public AttendanceResponse summary(@PathVariable Long eventId) {
        return attendanceService.summary(CurrentUser.id(), eventId);
    }
}
