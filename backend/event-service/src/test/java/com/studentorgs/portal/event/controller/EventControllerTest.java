package com.studentorgs.portal.event.controller;

import static com.studentorgs.portal.event.TestEvents.NOW;
import static com.studentorgs.portal.event.TestEvents.bearer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.studentorgs.portal.common.error.BusinessRuleException;
import com.studentorgs.portal.common.error.ForbiddenOperationException;
import com.studentorgs.portal.common.error.NotFoundException;
import com.studentorgs.portal.common.paging.PageResponse;
import com.studentorgs.portal.common.security.JwtService;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.event.domain.EventStatus;
import com.studentorgs.portal.event.domain.RsvpChoice;
import com.studentorgs.portal.event.dto.EventResponse;
import com.studentorgs.portal.event.service.EventService;

@WebMvcTest(EventController.class)
@ActiveProfiles("test")
class EventControllerTest {

    private static final String BODY = """
            {"title":"Kickoff","location":"ERB 101","startsAt":"2026-11-01T18:00:00Z","endsAt":"2026-11-01T20:00:00Z"}
            """;
    private static final EventResponse EVENT = new EventResponse(5L, "Kickoff", null, "ERB 101", NOW, NOW.plusSeconds(7200),
            EventStatus.SCHEDULED, 1L, NOW, NOW, 3, RsvpChoice.GOING);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @MockBean
    private EventService eventService;

    @Test
    void create_officer_returns201() throws Exception {
        when(eventService.create(eq(1L), any())).thenReturn(EVENT);

        mockMvc.perform(post("/events").header("Authorization", bearer(jwtService, 1, Role.OFFICER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    void create_member_returns403() throws Exception {
        mockMvc.perform(post("/events").header("Authorization", bearer(jwtService, 2, Role.MEMBER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verify(eventService, never()).create(anyLong(), any());
    }

    @Test
    void create_noToken_returns401() throws Exception {
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_missingTitleAndTimes_returns400() throws Exception {
        mockMvc.perform(post("/events").header("Authorization", bearer(jwtService, 1, Role.OFFICER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    void create_endBeforeStart_returns422() throws Exception {
        when(eventService.create(eq(1L), any())).thenThrow(new BusinessRuleException("endsAt must be after startsAt"));

        mockMvc.perform(post("/events").header("Authorization", bearer(jwtService, 1, Role.OFFICER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void update_officer_returnsEvent() throws Exception {
        when(eventService.update(eq(1L), eq(5L), any())).thenReturn(EVENT);

        mockMvc.perform(put("/events/5").header("Authorization", bearer(jwtService, 1, Role.OFFICER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void update_member_returns403() throws Exception {
        mockMvc.perform(put("/events/5").header("Authorization", bearer(jwtService, 2, Role.MEMBER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancel_officer_returnsEvent() throws Exception {
        when(eventService.cancel(1L, 5L)).thenReturn(EVENT);

        mockMvc.perform(post("/events/5/cancel").header("Authorization", bearer(jwtService, 1, Role.OFFICER_ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void list_member_passesFiltersAndReturnsPage() throws Exception {
        when(eventService.list(eq(2L), eq(NOW), isNull(), eq(true), eq(0), eq(10)))
                .thenReturn(new PageResponse<>(List.of(EVENT), 0, 10, 1, 1, true));

        mockMvc.perform(get("/events").param("from", NOW.toString()).param("upcoming", "true")
                        .param("page", "0").param("size", "10").header("Authorization", bearer(jwtService, 2, Role.MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].goingCount").value(3))
                .andExpect(jsonPath("$.content[0].myResponse").value("GOING"));
    }

    @Test
    void list_badDate_returns400() throws Exception {
        mockMvc.perform(get("/events").param("from", "yesterday").header("Authorization", bearer(jwtService, 2, Role.MEMBER)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_noToken_returns401() throws Exception {
        mockMvc.perform(get("/events")).andExpect(status().isUnauthorized());
    }

    @Test
    void list_notActiveMember_returns403() throws Exception {
        when(eventService.list(eq(3L), any(), any(), eq(false), any(), any()))
                .thenThrow(new ForbiddenOperationException("Your membership is not active"));

        mockMvc.perform(get("/events").header("Authorization", bearer(jwtService, 3, Role.MEMBER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_unknown_returns404() throws Exception {
        when(eventService.get(2L, 9L)).thenThrow(new NotFoundException("Event 9 not found"));

        mockMvc.perform(get("/events/9").header("Authorization", bearer(jwtService, 2, Role.MEMBER)))
                .andExpect(status().isNotFound());
    }
}
