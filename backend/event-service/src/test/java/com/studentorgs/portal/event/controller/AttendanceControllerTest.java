package com.studentorgs.portal.event.controller;

import static com.studentorgs.portal.event.TestEvents.NOW;
import static com.studentorgs.portal.event.TestEvents.bearer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
import com.studentorgs.portal.event.domain.AttendanceStatus;
import com.studentorgs.portal.event.dto.AttendanceEntryResponse;
import com.studentorgs.portal.event.dto.AttendanceResponse;
import com.studentorgs.portal.event.service.AttendanceService;

@WebMvcTest(AttendanceController.class)
@ActiveProfiles("test")
class AttendanceControllerTest {

    private static final AttendanceResponse SUMMARY = new AttendanceResponse(5L,
            List.of(new AttendanceEntryResponse(2L, "Ana", AttendanceStatus.PRESENT, 1L, NOW)), 1, 2, 0.5);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @MockBean
    private AttendanceService attendanceService;

    @Test
    void record_officer_returnsSummary() throws Exception {
        when(attendanceService.record(eq(1L), eq(5L), anyList())).thenReturn(SUMMARY);

        mockMvc.perform(put("/events/5/attendance").header("Authorization", bearer(jwtService, 1, Role.OFFICER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("[{\"userId\":2,\"status\":\"PRESENT\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(0.5))
                .andExpect(jsonPath("$.entries[0].recordedBy").value(1));
    }

    @Test
    void record_emptyList_returns400() throws Exception {
        mockMvc.perform(put("/events/5/attendance").header("Authorization", bearer(jwtService, 1, Role.OFFICER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isBadRequest());
        verify(attendanceService, never()).record(anyLong(), anyLong(), any());
    }

    @Test
    void record_moreThan100_returns400() throws Exception {
        String body = IntStream.rangeClosed(1, 101).mapToObj(i -> "{\"userId\":" + i + ",\"status\":\"PRESENT\"}")
                .collect(Collectors.joining(",", "[", "]"));

        mockMvc.perform(put("/events/5/attendance").header("Authorization", bearer(jwtService, 1, Role.OFFICER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void record_entryMissingStatus_returns400() throws Exception {
        mockMvc.perform(put("/events/5/attendance").header("Authorization", bearer(jwtService, 1, Role.OFFICER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("[{\"userId\":2}]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void record_beforeStart_returns422() throws Exception {
        when(attendanceService.record(eq(1L), eq(5L), anyList()))
                .thenThrow(new BusinessRuleException("Attendance can only be recorded after the event starts"));

        mockMvc.perform(put("/events/5/attendance").header("Authorization", bearer(jwtService, 1, Role.OFFICER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("[{\"userId\":2,\"status\":\"PRESENT\"}]"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void record_member_returns403() throws Exception {
        mockMvc.perform(put("/events/5/attendance").header("Authorization", bearer(jwtService, 2, Role.MEMBER))
                        .contentType(MediaType.APPLICATION_JSON).content("[{\"userId\":2,\"status\":\"PRESENT\"}]"))
                .andExpect(status().isForbidden());
    }

    @Test
    void summary_officer_returnsRate() throws Exception {
        when(attendanceService.summary(1L, 5L)).thenReturn(SUMMARY);

        mockMvc.perform(get("/events/5/attendance").header("Authorization", bearer(jwtService, 1, Role.OFFICER_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentCount").value(1))
                .andExpect(jsonPath("$.goingCount").value(2));
    }

    @Test
    void summary_noToken_returns401() throws Exception {
        mockMvc.perform(get("/events/5/attendance")).andExpect(status().isUnauthorized());
    }
}
