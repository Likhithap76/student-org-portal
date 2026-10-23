package com.studentorgs.portal.user.controller;

import static com.studentorgs.portal.user.TestUsers.bearer;
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
import com.studentorgs.portal.common.security.UserStatus;
import com.studentorgs.portal.user.dto.UserNameResponse;
import com.studentorgs.portal.user.dto.UserStatusResponse;
import com.studentorgs.portal.user.service.UserService;

@WebMvcTest(InternalUserController.class)
@ActiveProfiles("test")
class InternalUserControllerTest {

    private static final String TOKEN = "test-internal-token";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @MockBean
    private UserService userService;

    @Test
    void status_internalToken_returnsStatusAndRole() throws Exception {
        when(userService.status(7L)).thenReturn(new UserStatusResponse(UserStatus.ACTIVE, Role.MEMBER));

        mockMvc.perform(get("/internal/users/7/status").header(InternalTokenFilter.HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.role").value("MEMBER"));
    }

    @Test
    void status_unknownUser_returns404() throws Exception {
        when(userService.status(7L)).thenThrow(new NotFoundException("User 7 not found"));

        mockMvc.perform(get("/internal/users/7/status").header(InternalTokenFilter.HEADER, TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void status_noToken_returns401() throws Exception {
        mockMvc.perform(get("/internal/users/7/status")).andExpect(status().isUnauthorized());
    }

    @Test
    void status_userJwtInsteadOfInternalToken_returns403() throws Exception {
        mockMvc.perform(get("/internal/users/7/status")
                        .header("Authorization", bearer(jwtService, 1, Role.OFFICER_ADMIN, UserStatus.ACTIVE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void batch_ids_returnsNames() throws Exception {
        when(userService.names(List.of(7L, 8L))).thenReturn(List.of(new UserNameResponse(7L, "U7")));

        mockMvc.perform(get("/internal/users/batch").param("ids", "7,8").header(InternalTokenFilter.HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].displayName").value("U7"));
    }

    @Test
    void activeIds_returnsIds() throws Exception {
        when(userService.activeIds()).thenReturn(List.of(1L, 7L));

        mockMvc.perform(get("/internal/users/active-ids").header(InternalTokenFilter.HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1]").value(7));
    }
}
