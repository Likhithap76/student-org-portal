package com.studentorgs.portal.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.studentorgs.portal.common.client.NotificationClient;
import com.studentorgs.portal.common.client.NotificationKind;
import com.studentorgs.portal.common.security.InternalTokenFilter;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;
import com.studentorgs.portal.user.domain.AppUser;
import com.studentorgs.portal.user.repository.AppUserRepository;

/** End-to-end flows on in-memory H2: register → approve → login → protected call, and the reject path. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserFlowIntegrationTest {

    private static final String OFFICER_ADMIN_EMAIL = "admin@uta.edu";
    private static final String PASSWORD = "secret123";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbc;
    @MockBean
    private NotificationClient notificationClient;

    @BeforeEach
    void ensureAdmin() {
        if (users.findByEmail(OFFICER_ADMIN_EMAIL).isEmpty()) {
            users.save(new AppUser(OFFICER_ADMIN_EMAIL, "Admin", passwordEncoder.encode(PASSWORD), Role.OFFICER_ADMIN,
                    UserStatus.ACTIVE, Instant.now()));
        }
    }

    // ---------- helpers ----------

    private JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private long register(String email, String name) throws Exception {
        return body(mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"displayName\":\"%s\",\"password\":\"%s\"}".formatted(email, name, PASSWORD)))
                .andExpect(status().isCreated()))
                .get("id").asLong();
    }

    private ResultActions login(String email) throws Exception {
        return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)));
    }

    private String token(String email) throws Exception {
        return "Bearer " + body(login(email).andExpect(status().isOk())).get("accessToken").asText();
    }

    private long myApplicationId(String bearer) throws Exception {
        return body(mockMvc.perform(get("/memberships/me").header("Authorization", bearer))
                .andExpect(status().isOk())).get("id").asLong();
    }

    private int auditRows(String action, long targetId) {
        return jdbc.queryForObject("select count(*) from audit_event where action = ? and target_id = ?",
                Integer.class, action, targetId);
    }

    // ---------- flows ----------

    @Test
    void registerApproveLogin_thenProtectedCall_works() throws Exception {
        long anaId = register("ana@uta.edu", "Ana");
        String pendingToken = token("ana@uta.edu");

        // Pending users can see their application but not member features.
        long applicationId = myApplicationId(pendingToken);
        mockMvc.perform(get("/users/roster").header("Authorization", pendingToken)).andExpect(status().isForbidden());

        String admin = token(OFFICER_ADMIN_EMAIL);
        mockMvc.perform(get("/memberships").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == %d)].applicantEmail".formatted(applicationId)).value("ana@uta.edu"));

        mockMvc.perform(post("/memberships/{id}/approve", applicationId).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("APPROVED"))
                .andExpect(jsonPath("$.decidedBy").isNumber())
                .andExpect(jsonPath("$.decidedAt").isNotEmpty());

        assertThat(users.findById(anaId).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(auditRows("membership.approve", applicationId)).isEqualTo(1);
        verify(notificationClient).send(argThat(n -> n.recipientIds().contains(anaId)
                && n.kind() == NotificationKind.APPLICATION_DECISION && n.referenceId() == applicationId));

        // Approving twice is a business-rule error.
        mockMvc.perform(post("/memberships/{id}/approve", applicationId).header("Authorization", admin))
                .andExpect(status().isUnprocessableEntity());

        // New login → active token → protected call works; the roster stays officer_admin-only.
        String activeToken = token("ana@uta.edu");
        login("ana@uta.edu").andExpect(jsonPath("$.user.status").value("ACTIVE"));
        mockMvc.perform(get("/auth/me").header("Authorization", activeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(get("/users/roster").header("Authorization", activeToken))
                .andExpect(status().isForbidden());

        // Members cannot review applications.
        mockMvc.perform(get("/memberships").header("Authorization", activeToken)).andExpect(status().isForbidden());
    }

    @Test
    void reject_userStaysPending_andCanReapplyOnce() throws Exception {
        long bobId = register("bob@uta.edu", "Bob");
        String bob = token("bob@uta.edu");
        long applicationId = myApplicationId(bob);

        mockMvc.perform(post("/memberships/{id}/reject", applicationId).header("Authorization", token(OFFICER_ADMIN_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("REJECTED"));

        assertThat(users.findById(bobId).orElseThrow().getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(auditRows("membership.reject", applicationId)).isEqualTo(1);
        mockMvc.perform(get("/memberships/me").header("Authorization", bob))
                .andExpect(jsonPath("$.decision").value("REJECTED"));

        mockMvc.perform(post("/memberships/reapply").header("Authorization", bob))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decision").value("PENDING"));
        mockMvc.perform(post("/memberships/reapply").header("Authorization", bob))
                .andExpect(status().isConflict());
    }

    @Test
    void register_duplicateEmailDifferentCase_returns409() throws Exception {
        register("carol@uta.edu", "Carol");

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"CAROL@uta.edu\",\"displayName\":\"C\",\"password\":\"secret123\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void deactivate_blocksLogin_andReactivateRestoresIt() throws Exception {
        long danId = register("dan@uta.edu", "Dan");
        String admin = token(OFFICER_ADMIN_EMAIL);
        mockMvc.perform(post("/memberships/{id}/approve", myApplicationId(token("dan@uta.edu"))).header("Authorization", admin))
                .andExpect(status().isOk());
        String danToken = token("dan@uta.edu");

        mockMvc.perform(post("/users/{id}/deactivate", danId).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        assertThat(auditRows("user.deactivate", danId)).isEqualTo(1);

        login("dan@uta.edu").andExpect(status().isForbidden());
        // A token issued before deactivation no longer works in user-service.
        mockMvc.perform(get("/auth/me").header("Authorization", danToken)).andExpect(status().isForbidden());

        mockMvc.perform(post("/users/{id}/reactivate", danId).header("Authorization", admin))
                .andExpect(status().isOk());
        assertThat(auditRows("user.reactivate", danId)).isEqualTo(1);
        login("dan@uta.edu").andExpect(status().isOk());
    }

    @Test
    void changeRole_promoteDemote_lastOfficerAdminCannotBeDemoted() throws Exception {
        long eveId = register("eve@uta.edu", "Eve");
        String admin = token(OFFICER_ADMIN_EMAIL);
        mockMvc.perform(post("/memberships/{id}/approve", myApplicationId(token("eve@uta.edu"))).header("Authorization", admin));

        mockMvc.perform(put("/users/{id}/role", eveId).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"OFFICER_ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("OFFICER_ADMIN"));
        assertThat(auditRows("user.role_change", eveId)).isEqualTo(1);

        // An officer_admin sees full roster rows.
        mockMvc.perform(get("/users/roster").param("q", "eve").header("Authorization", token("eve@uta.edu")))
                .andExpect(jsonPath("$.content[0].email").value("eve@uta.edu"));

        // Two officer_admins: demoting one is allowed.
        mockMvc.perform(put("/users/{id}/role", eveId).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"));

        // Now the seed admin is the last officer_admin and cannot be demoted.
        long adminId = users.findByEmail(OFFICER_ADMIN_EMAIL).orElseThrow().getId();
        mockMvc.perform(put("/users/{id}/role", adminId).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void internalEndpoints_withInternalToken_work() throws Exception {
        long frankId = register("frank@uta.edu", "Frank");

        mockMvc.perform(get("/internal/users/{id}/status", frankId).header(InternalTokenFilter.HEADER, "test-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        mockMvc.perform(get("/internal/users/batch").param("ids", String.valueOf(frankId))
                        .header(InternalTokenFilter.HEADER, "test-internal-token"))
                .andExpect(jsonPath("$[0].displayName").value("Frank"));
        mockMvc.perform(get("/internal/users/active-ids").header(InternalTokenFilter.HEADER, "test-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
