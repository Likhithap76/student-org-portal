package com.studentorgs.portal.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.studentorgs.portal.common.client.UserDirectoryClient;
import com.studentorgs.portal.common.client.UserDirectoryClient.UserStatusInfo;
import com.studentorgs.portal.common.security.JwtService;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;
import com.studentorgs.portal.event.repository.AttendanceRepository;

/**
 * Events, RSVP and attendance end to end on in-memory H2. user-service is replaced by a mocked
 * UserDirectoryClient; a movable clock lets the test step past an event's start time.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventFlowIntegrationTest {

    private static final long OFFICER_ADMIN_ID = 1L;
    private static final long ANA = 2L;
    private static final long BOB = 3L;
    private static final long PENDING = 4L;
    private static final Instant T0 = Instant.parse("2026-10-20T15:00:00Z");

    /** A clock the test can move forward. */
    static class MovableClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(T0);

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean
        MovableClock testClock() {
            return new MovableClock();
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private MovableClock clock;
    @Autowired
    private AttendanceRepository attendanceRepository;
    @MockBean
    private UserDirectoryClient users;

    @BeforeEach
    void setUp() {
        clock.now.set(T0);
        when(users.status(OFFICER_ADMIN_ID)).thenReturn(Optional.of(new UserStatusInfo(UserStatus.ACTIVE, Role.OFFICER_ADMIN)));
        when(users.status(ANA)).thenReturn(Optional.of(new UserStatusInfo(UserStatus.ACTIVE, Role.MEMBER)));
        when(users.status(BOB)).thenReturn(Optional.of(new UserStatusInfo(UserStatus.ACTIVE, Role.MEMBER)));
        when(users.status(PENDING)).thenReturn(Optional.of(new UserStatusInfo(UserStatus.PENDING, Role.MEMBER)));
        when(users.names(anyCollection())).thenReturn(Map.of(ANA, "Ana", BOB, "Bob"));
    }

    private String as(long id, Role role) {
        return TestEvents.bearer(jwtService, id, role);
    }

    private long createEvent(String title, Instant start) throws Exception {
        String body = """
                {"title":"%s","location":"ERB 101","startsAt":"%s","endsAt":"%s"}
                """.formatted(title, start, start.plus(Duration.ofHours(2)));
        String json = mockMvc.perform(post("/events").header("Authorization", as(OFFICER_ADMIN_ID, Role.OFFICER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }

    private void rsvp(long userId, long eventId, String choice, int expectedStatus) throws Exception {
        mockMvc.perform(put("/events/{id}/rsvp", eventId).header("Authorization", as(userId, Role.MEMBER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"response\":\"" + choice + "\"}"))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void createRsvpThenUpdate_countsAndMyResponseFollow() throws Exception {
        long eventId = createEvent("Kickoff", T0.plus(Duration.ofHours(1)));

        rsvp(ANA, eventId, "GOING", 200);
        rsvp(BOB, eventId, "GOING", 200);
        mockMvc.perform(get("/events/{id}", eventId).header("Authorization", as(ANA, Role.MEMBER)))
                .andExpect(jsonPath("$.goingCount").value(2))
                .andExpect(jsonPath("$.myResponse").value("GOING"));

        // Changing an RSVP updates the same row (find-then-save), it does not add one.
        rsvp(ANA, eventId, "NOT_GOING", 200);
        mockMvc.perform(get("/events").param("upcoming", "true").header("Authorization", as(ANA, Role.MEMBER)))
                .andExpect(jsonPath("$.content[?(@.id == %d)].goingCount".formatted(eventId)).value(1))
                .andExpect(jsonPath("$.content[?(@.id == %d)].myResponse".formatted(eventId)).value("NOT_GOING"));
        mockMvc.perform(get("/events/{id}/rsvps", eventId).header("Authorization", as(OFFICER_ADMIN_ID, Role.OFFICER_ADMIN)))
                .andExpect(jsonPath("$.length()").value(2));

        // Pending members cannot RSVP; nobody can RSVP once the event has started.
        rsvp(PENDING, eventId, "GOING", 403);
        clock.now.set(T0.plus(Duration.ofMinutes(61)));
        rsvp(BOB, eventId, "NOT_GOING", 422);
    }

    @Test
    void attendance_createThenUpdate_recordsOfficerAndRate() throws Exception {
        long eventId = createEvent("Workshop", T0.plus(Duration.ofHours(1)));
        rsvp(ANA, eventId, "GOING", 200);
        rsvp(BOB, eventId, "GOING", 200);
        String officer = as(OFFICER_ADMIN_ID, Role.OFFICER_ADMIN);

        // Not before the event starts.
        mockMvc.perform(put("/events/{id}/attendance", eventId).header("Authorization", officer)
                        .contentType(MediaType.APPLICATION_JSON).content("[{\"userId\":2,\"status\":\"PRESENT\"}]"))
                .andExpect(status().isUnprocessableEntity());

        clock.now.set(T0.plus(Duration.ofMinutes(90)));
        mockMvc.perform(put("/events/{id}/attendance", eventId).header("Authorization", officer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"userId\":2,\"status\":\"PRESENT\"},{\"userId\":3,\"status\":\"ABSENT\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentCount").value(1))
                .andExpect(jsonPath("$.goingCount").value(2))
                .andExpect(jsonPath("$.rate").value(0.5));

        // Update Bob: same row, new status.
        mockMvc.perform(put("/events/{id}/attendance", eventId).header("Authorization", officer)
                        .contentType(MediaType.APPLICATION_JSON).content("[{\"userId\":3,\"status\":\"PRESENT\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presentCount").value(2))
                .andExpect(jsonPath("$.rate").value(1.0));

        assertThat(attendanceRepository.findByIdEventId(eventId)).hasSize(2)
                .allSatisfy(a -> assertThat(a.getRecordedBy()).isEqualTo(OFFICER_ADMIN_ID));
        mockMvc.perform(get("/events/{id}/attendance", eventId).header("Authorization", officer))
                .andExpect(jsonPath("$.entries[0].displayName").value("Ana"));
    }

    @Test
    void cancelledEvent_rejectsRsvpAndEdits() throws Exception {
        long eventId = createEvent("Social", T0.plus(Duration.ofDays(1)));
        String officer = as(OFFICER_ADMIN_ID, Role.OFFICER_ADMIN);

        mockMvc.perform(post("/events/{id}/cancel", eventId).header("Authorization", officer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        rsvp(ANA, eventId, "GOING", 422);
        mockMvc.perform(put("/events/{id}", eventId).header("Authorization", officer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"startsAt\":\"%s\",\"endsAt\":\"%s\"}"
                                .formatted(T0.plus(Duration.ofDays(2)), T0.plus(Duration.ofDays(3)))))
                .andExpect(status().isUnprocessableEntity());
        // Still listed (nothing is deleted).
        mockMvc.perform(get("/events/{id}", eventId).header("Authorization", as(ANA, Role.MEMBER)))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void create_startInPast_returns422() throws Exception {
        mockMvc.perform(post("/events").header("Authorization", as(OFFICER_ADMIN_ID, Role.OFFICER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Late\",\"startsAt\":\"%s\",\"endsAt\":\"%s\"}"
                                .formatted(T0.minusSeconds(60), T0.plusSeconds(3600))))
                .andExpect(status().isUnprocessableEntity());
    }
}
