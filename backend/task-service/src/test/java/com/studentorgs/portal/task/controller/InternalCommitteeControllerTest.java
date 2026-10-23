package com.studentorgs.portal.task.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.studentorgs.portal.common.error.NotFoundException;
import com.studentorgs.portal.common.security.InternalTokenFilter;
import com.studentorgs.portal.common.security.JwtService;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.task.TestTasks;
import com.studentorgs.portal.task.dto.MemberNameResponse;
import com.studentorgs.portal.task.service.CommitteeService;

@WebMvcTest(InternalCommitteeController.class)
@ActiveProfiles("test")
class InternalCommitteeControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @MockBean
    private CommitteeService committeeService;

    @Test
    void members_internalToken_returnsList() throws Exception {
        when(committeeService.memberNames(5L)).thenReturn(List.of(new MemberNameResponse(2L, "Ana")));

        mockMvc.perform(get("/internal/committees/5/members").header(InternalTokenFilter.HEADER, "test-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("Ana"));
    }

    @Test
    void members_unknownCommittee_returns404() throws Exception {
        when(committeeService.memberNames(9L)).thenThrow(new NotFoundException("Committee 9 not found"));

        mockMvc.perform(get("/internal/committees/9/members").header(InternalTokenFilter.HEADER, "test-internal-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void members_noToken_returns401() throws Exception {
        mockMvc.perform(get("/internal/committees/5/members")).andExpect(status().isUnauthorized());
    }

    @Test
    void members_userJwt_returns403() throws Exception {
        mockMvc.perform(get("/internal/committees/5/members")
                        .header("Authorization", TestTasks.bearer(jwtService, 1, Role.OFFICER_ADMIN)))
                .andExpect(status().isForbidden());
    }
}
