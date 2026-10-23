package com.studentorgs.portal.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";
    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private static final AuthenticatedUser USER =
            new AuthenticatedUser(42L, "ana@uta.edu", "Ana", Role.OFFICER_ADMIN, UserStatus.ACTIVE);

    private final JwtService service = new JwtService(SECRET, Duration.ofHours(2), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void parse_tokenJustIssued_returnsSameUser() {
        assertThat(service.parse(service.issue(USER))).isEqualTo(USER);
    }

    @Test
    void parse_afterExpiry_throwsInvalidToken() {
        String token = service.issue(USER);
        JwtService later = new JwtService(SECRET, Duration.ofHours(2),
                Clock.fixed(NOW.plus(Duration.ofHours(2)).plusSeconds(1), ZoneOffset.UTC));

        assertThatThrownBy(() -> later.parse(token)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void parse_signedWithOtherSecret_throwsInvalidToken() {
        JwtService other = new JwtService("another-secret-another-secret-123456", Duration.ofHours(2), Clock.systemUTC());

        assertThatThrownBy(() -> service.parse(other.issue(USER))).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void parse_garbage_throwsInvalidToken() {
        assertThatThrownBy(() -> service.parse("abc.def.ghi")).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void constructor_shortSecret_throws() {
        assertThatThrownBy(() -> new JwtService("too-short", Duration.ofHours(2), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void expiresInSeconds_twoHours_returns7200() {
        assertThat(service.expiresInSeconds()).isEqualTo(7200);
    }
}
