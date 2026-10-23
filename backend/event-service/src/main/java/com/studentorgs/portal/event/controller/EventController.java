package com.studentorgs.portal.event.controller;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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
import com.studentorgs.portal.event.dto.EventRequest;
import com.studentorgs.portal.event.dto.EventResponse;
import com.studentorgs.portal.event.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OFFICER_ADMIN')")
    @Operation(summary = "Schedule an event (OFFICER_ADMIN). startsAt must be in the future and before endsAt")
    public EventResponse create(@Valid @RequestBody EventRequest request) {
        return eventService.create(CurrentUser.id(), request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OFFICER_ADMIN')")
    @Operation(summary = "Edit an event (OFFICER_ADMIN). Not allowed on cancelled events")
    public EventResponse update(@PathVariable Long id, @Valid @RequestBody EventRequest request) {
        return eventService.update(CurrentUser.id(), id, request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('OFFICER_ADMIN')")
    @Operation(summary = "Cancel an event (OFFICER_ADMIN). Nothing is deleted")
    public EventResponse cancel(@PathVariable Long id) {
        return eventService.cancel(CurrentUser.id(), id);
    }

    @GetMapping
    @Operation(summary = "Events by start time, with goingCount and myResponse (active members)")
    public PageResponse<EventResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "false") boolean upcoming,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return eventService.list(CurrentUser.id(), from, to, upcoming, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Event detail with counts (active members)")
    public EventResponse get(@PathVariable Long id) {
        return eventService.get(CurrentUser.id(), id);
    }
}
