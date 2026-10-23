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
import com.studentorgs.portal.event.dto.RsvpEntryResponse;
import com.studentorgs.portal.event.dto.RsvpRequest;
import com.studentorgs.portal.event.service.RsvpService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/events/{eventId}")
@RequiredArgsConstructor
public class RsvpController {

    private final RsvpService rsvpService;

    @PutMapping("/rsvp")
    @Operation(summary = "Create or change my RSVP: GOING or NOT_GOING (before the event starts)")
    public RsvpEntryResponse respond(@PathVariable Long eventId, @Valid @RequestBody RsvpRequest request) {
        return rsvpService.respond(CurrentUser.id(), eventId, request);
    }

    @GetMapping("/rsvps")
    @PreAuthorize("hasRole('OFFICER_ADMIN')")
    @Operation(summary = "All RSVPs for an event (OFFICER_ADMIN)")
    public List<RsvpEntryResponse> list(@PathVariable Long eventId) {
        return rsvpService.list(CurrentUser.id(), eventId);
    }
}
