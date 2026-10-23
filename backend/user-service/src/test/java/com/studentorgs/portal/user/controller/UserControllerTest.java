package com.studentorgs.portal.user.controller;

import static com.studentorgs.portal.user.TestUsers.NOW;
import static com.studentorgs.portal.user.TestUsers.bearer;
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
import com.studentorgs.portal.common.paging.PageResponse;
import com.studentorgs.portal.common.security.JwtService;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;
import com.studentorgs.portal.user.dto.RosterEntryResponse;
import com.studentorgs.portal.user.dto.UserResponse;
import com.studentorgs.portal.user.service.UserService;

@WebMvcTest(UserController.class)
@ActiveProfiles("test")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @MockBean
    private UserService userService;

    private String as(long id, Role role) {
        return bearer(jwtService, id, role, UserStatus.ACTIVE);
    }

    private static UserResponse user7(Role role, UserStatus status) {
        return new UserResponse(7L, "u7@uta.edu", "U7", role, status, NOW);
    }

    @Test
    void roster_member_returns403() throws Exception {
        mockMvc.perform(get("/users/roster").header("Authorization", as(2, Role.MEMBER)))
                .andExpect(status().isForbidden());
        verify(userService, never()).roster(anyLong(), any(), any(), any(), any());
    }

    @Test
    void roster_officerWithFilters_passesThem() throws Exception {
        when(userService.roster(1L, "ana", Role.OFFICER_ADMIN, 1, 10))
                .thenReturn(new PageResponse<>(List.of(), 1, 10, 0, 0, true));

        mockMvc.perform(get("/users/roster").param("q", "ana").param("role", "OFFICER_ADMIN").param("page", "1")
                        .param("size", "10").header("Authorization", as(1, Role.OFFICER_ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void roster_pendingUser_returns403() throws Exception {
        when(userService.roster(eq(5L), any(), any(), any(), any()))
                .thenThrow(new ForbiddenOperationException("Your membership is not active yet"));

        mockMvc.perform(get("/users/roster").header("Authorization", bearer(jwtService, 5, Role.MEMBER, UserStatus.PENDING)))
                .andExpect(status().isForbidden());
    }

    @Test
    void roster_noToken_returns401() throws Exception {
        mockMvc.perform(get("/users/roster")).andExpect(status().isUnauthorized());
    }

    @Test
    void changeRole_admin_returnsUpdatedUser() throws Exception {
        when(userService.changeRole(1L, 7L, Role.OFFICER_ADMIN)).thenReturn(user7(Role.OFFICER_ADMIN, UserStatus.ACTIVE));

        mockMvc.perform(put("/users/7/role").header("Authorization", as(1, Role.OFFICER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"OFFICER_ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("OFFICER_ADMIN"));
    }

    @Test
    void changeRole_member_returns403() throws Exception {
        mockMvc.perform(put("/users/7/role").header("Authorization", as(2, Role.MEMBER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"OFFICER_ADMIN\"}"))
                .andExpect(status().isForbidden());
        verify(userService, never()).changeRole(anyLong(), anyLong(), any());
    }

    @Test
    void changeRole_missingRole_returns400() throws Exception {
        mockMvc.perform(put("/users/7/role").header("Authorization", as(1, Role.OFFICER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changeRole_lastAdmin_returns422() throws Exception {
        when(userService.changeRole(1L, 1L, Role.MEMBER)).thenThrow(new BusinessRuleException("Cannot demote the last admin"));

        mockMvc.perform(put("/users/1/role").header("Authorization", as(1, Role.OFFICER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Cannot demote the last admin"));
    }

    @Test
    void deactivate_officer_returnsInactiveUser() throws Exception {
        when(userService.deactivate(1L, 7L)).thenReturn(user7(Role.MEMBER, UserStatus.INACTIVE));

        mockMvc.perform(post("/users/7/deactivate").header("Authorization", as(1, Role.OFFICER_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void deactivate_member_returns403() throws Exception {
        mockMvc.perform(post("/users/7/deactivate").header("Authorization", as(2, Role.MEMBER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deactivate_noToken_returns401() throws Exception {
        mockMvc.perform(post("/users/7/deactivate")).andExpect(status().isUnauthorized());
    }

    @Test
    void reactivate_officer_returnsActiveUser() throws Exception {
        when(userService.reactivate(1L, 7L)).thenReturn(user7(Role.MEMBER, UserStatus.ACTIVE));

        mockMvc.perform(post("/users/7/reactivate").header("Authorization", as(1, Role.OFFICER_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void reactivate_member_returns403() throws Exception {
        mockMvc.perform(post("/users/7/reactivate").header("Authorization", as(2, Role.MEMBER)))
                .andExpect(status().isForbidden());
    }
}
