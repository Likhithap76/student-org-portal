package com.studentorgs.portal.event.controller;

import static com.studentorgs.portal.event.TestEvents.NOW;
import static com.studentorgs.portal.event.TestEvents.bearer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.studentorgs.portal.common.security.JwtService;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.event.domain.RsvpChoice;
import com.studentorgs.portal.event.dto.RsvpEntryResponse;
import com.studentorgs.portal.event.dto.RsvpRequest;
import com.studentorgs.portal.event.service.RsvpService;

@WebMvcTest(RsvpController.class)
@ActiveProfiles("test")
class RsvpControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @MockBean
    private RsvpService rsvpService;

    @Test
    void respond_member_returnsRsvp() throws Exception {
        when(rsvpService.respond(2L, 5L, new RsvpRequest(RsvpChoice.NOT_GOING)))
                .thenReturn(new RsvpEntryResponse(5L, 2L, null, RsvpChoice.NOT_GOING, NOW));

        mockMvc.perform(put("/events/5/rsvp").header("Authorization", bearer(jwtService, 2, Role.MEMBER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"response\":\"NOT_GOING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("NOT_GOING"))
                .andExpect(jsonPath("$.displayName").doesNotExist());
    }

    @Test
    void respond_invalidChoice_returns400() throws Exception {
        mockMvc.perform(put("/events/5/rsvp").header("Authorization", bearer(jwtService, 2, Role.MEMBER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"response\":\"PERHAPS\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void respond_maybe_returns400() throws Exception {
        mockMvc.perform(put("/events/5/rsvp").header("Authorization", bearer(jwtService, 2, Role.MEMBER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"response\":\"MAYBE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void respond_missingChoice_returns400() throws Exception {
        mockMvc.perform(put("/events/5/rsvp").header("Authorization", bearer(jwtService, 2, Role.MEMBER))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void respond_cancelledEvent_returns422() throws Exception {
        when(rsvpService.respond(2L, 5L, new RsvpRequest(RsvpChoice.GOING)))
                .thenThrow(new BusinessRuleException("You cannot RSVP to a cancelled event"));

        mockMvc.perform(put("/events/5/rsvp").header("Authorization", bearer(jwtService, 2, Role.MEMBER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"response\":\"GOING\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void respond_noToken_returns401() throws Exception {
        mockMvc.perform(put("/events/5/rsvp").contentType(MediaType.APPLICATION_JSON).content("{\"response\":\"GOING\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_officer_returnsRsvps() throws Exception {
        when(rsvpService.list(1L, 5L)).thenReturn(List.of(new RsvpEntryResponse(5L, 2L, "Ana", RsvpChoice.GOING, NOW)));

        mockMvc.perform(get("/events/5/rsvps").header("Authorization", bearer(jwtService, 1, Role.OFFICER_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("Ana"));
    }

    @Test
    void list_member_returns403() throws Exception {
        mockMvc.perform(get("/events/5/rsvps").header("Authorization", bearer(jwtService, 2, Role.MEMBER)))
                .andExpect(status().isForbidden());
    }
}
