package com.studentorgs.portal.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Checks the shared security chain as a @WebMvcTest slice sees it (same as service controller tests will). */
@WebMvcTest(SecurityWebMvcTest.ProbeController.class)
@ActiveProfiles("test")
@Import(SecurityWebMvcTest.ProbeController.class)
class SecurityWebMvcTest {

    @RestController
    static class ProbeController {

        @GetMapping("/probe/me")
        Long me() {
            return CurrentUser.id();
        }

        @GetMapping("/probe/officer")
        @PreAuthorize("hasRole('OFFICER_ADMIN')")
        String officer() {
            return "ok";
        }

        @GetMapping("/internal/probe")
        String internal() {
            return "internal";
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private String bearer(Role role) {
        return "Bearer " + jwtService.issue(new AuthenticatedUser(5L, "u@uta.edu", "U", role, UserStatus.ACTIVE));
    }

    @Test
    void me_noToken_returns401Problem() throws Exception {
        mockMvc.perform(get("/probe/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void me_invalidToken_returns401() throws Exception {
        mockMvc.perform(get("/probe/me").header("Authorization", "Bearer nope"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_validToken_returnsUserId() throws Exception {
        mockMvc.perform(get("/probe/me").header("Authorization", bearer(Role.MEMBER)))
                .andExpect(status().isOk())
                .andExpect(content().string("5"));
    }

    @Test
    void officer_memberToken_returns403Problem() throws Exception {
        mockMvc.perform(get("/probe/officer").header("Authorization", bearer(Role.MEMBER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void officer_adminToken_allowedByHierarchy() throws Exception {
        mockMvc.perform(get("/probe/officer").header("Authorization", bearer(Role.OFFICER_ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void internal_userToken_returns403() throws Exception {
        mockMvc.perform(get("/internal/probe").header("Authorization", bearer(Role.OFFICER_ADMIN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void internal_wrongInternalToken_returns401() throws Exception {
        mockMvc.perform(get("/internal/probe").header(InternalTokenFilter.HEADER, "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internal_validInternalToken_returns200() throws Exception {
        mockMvc.perform(get("/internal/probe").header(InternalTokenFilter.HEADER, "test-internal-token"))
                .andExpect(status().isOk())
                .andExpect(content().string("internal"));
    }

    @Test
    void request_withSafeRequestId_echoesIt() throws Exception {
        mockMvc.perform(get("/probe/me").header("X-Request-Id", "abc-123"))
                .andExpect(header().string("X-Request-Id", "abc-123"));
    }
}
