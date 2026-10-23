package com.studentorgs.portal.user.controller;

import static com.studentorgs.portal.user.TestUsers.NOW;
import static com.studentorgs.portal.user.TestUsers.bearer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.studentorgs.portal.common.error.ConflictException;
import com.studentorgs.portal.common.error.ForbiddenOperationException;
import com.studentorgs.portal.common.security.JwtService;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;
import com.studentorgs.portal.user.dto.ChangePasswordRequest;
import com.studentorgs.portal.user.dto.LoginResponse;
import com.studentorgs.portal.user.dto.UserResponse;
import com.studentorgs.portal.user.service.AuthService;

@WebMvcTest(AuthController.class)
@ActiveProfiles("test")
class AuthControllerTest {

    private static final UserResponse ANA =
            new UserResponse(3L, "ana@uta.edu", "Ana", Role.MEMBER, UserStatus.PENDING, NOW);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @MockBean
    private AuthService authService;

    private static String json(String email, String name, String password) {
        return "{\"email\":\"%s\",\"displayName\":\"%s\",\"password\":\"%s\"}".formatted(email, name, password);
    }

    @Test
    void register_validBody_returns201() throws Exception {
        when(authService.register(any())).thenReturn(ANA);

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("ana@uta.edu", "Ana", "secret123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void register_weakPassword_returns400WithFieldError() throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("ana@uta.edu", "Ana", "lettersonly")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));
        verify(authService, never()).register(any());
    }

    @Test
    void register_shortPassword_returns400() throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("ana@uta.edu", "Ana", "ab1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("not-an-email", "Ana", "secret123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        when(authService.register(any())).thenThrow(new ConflictException("An account with this email already exists"));

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("ana@uta.edu", "Ana", "secret123")))
                .andExpect(status().isConflict());
    }

    @Test
    void login_validCredentials_returnsToken() throws Exception {
        when(authService.login(any())).thenReturn(LoginResponse.bearer("tok", 7200, ANA));

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@uta.edu\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("tok"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(7200))
                .andExpect(jsonPath("$.user.id").value(3));
    }

    @Test
    void login_badCredentials_returns401() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("x"));

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@uta.edu\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void login_inactiveUser_returns403() throws Exception {
        when(authService.login(any())).thenThrow(new ForbiddenOperationException("This account is inactive"));

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@uta.edu\",\"password\":\"secret123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void login_missingFields_returns400() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void me_noToken_returns401() throws Exception {
        mockMvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void me_pendingUserToken_returnsProfile() throws Exception {
        when(authService.me(3L)).thenReturn(ANA);

        mockMvc.perform(get("/auth/me").header("Authorization", bearer(jwtService, 3, Role.MEMBER, UserStatus.PENDING)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@uta.edu"));
    }

    @Test
    void me_deactivatedAccount_returns403() throws Exception {
        when(authService.me(3L)).thenThrow(new ForbiddenOperationException("Your account is inactive"));

        mockMvc.perform(get("/auth/me").header("Authorization", bearer(jwtService, 3, Role.MEMBER, UserStatus.ACTIVE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateMe_blankName_returns400() throws Exception {
        mockMvc.perform(put("/auth/me").header("Authorization", bearer(jwtService, 3, Role.MEMBER, UserStatus.ACTIVE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateMe_validName_returnsProfile() throws Exception {
        when(authService.updateProfile(eq(3L), any())).thenReturn(ANA);

        mockMvc.perform(put("/auth/me").header("Authorization", bearer(jwtService, 3, Role.MEMBER, UserStatus.ACTIVE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Ana\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void changePassword_valid_returns204() throws Exception {
        mockMvc.perform(put("/auth/me/password").header("Authorization", bearer(jwtService, 3, Role.MEMBER, UserStatus.ACTIVE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"secret123\",\"newPassword\":\"newpass456\"}"))
                .andExpect(status().isNoContent());
        verify(authService).changePassword(3L, new ChangePasswordRequest("secret123", "newpass456"));
    }

    @Test
    void changePassword_noToken_returns401() throws Exception {
        mockMvc.perform(put("/auth/me/password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"secret123\",\"newPassword\":\"newpass456\"}"))
                .andExpect(status().isUnauthorized());
    }
}
