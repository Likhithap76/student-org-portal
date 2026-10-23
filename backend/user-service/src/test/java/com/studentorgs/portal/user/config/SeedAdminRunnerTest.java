package com.studentorgs.portal.user.config;

import static com.studentorgs.portal.user.TestUsers.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;
import com.studentorgs.portal.user.domain.AppUser;
import com.studentorgs.portal.user.repository.AppUserRepository;

@ExtendWith(MockitoExtension.class)
class SeedAdminRunnerTest {

    @Mock
    private AppUserRepository users;

    private SeedAdminRunner runner(String email, String password) {
        return new SeedAdminRunner(new SeedAdminProperties(true, email, password, null), users,
                new BCryptPasswordEncoder(4), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void run_noAdminYet_createsActiveAdmin() {
        when(users.countByRole(Role.OFFICER_ADMIN)).thenReturn(0L);
        when(users.existsByEmail("admin@uta.edu")).thenReturn(false);

        runner(" Admin@UTA.edu ", "adminpass1").run();

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@uta.edu");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.OFFICER_ADMIN);
        assertThat(saved.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getValue().getDisplayName()).isEqualTo("Administrator");
    }

    @Test
    void run_adminExists_skips() {
        when(users.countByRole(Role.OFFICER_ADMIN)).thenReturn(1L);

        runner("admin@uta.edu", "adminpass1").run();

        verify(users, never()).save(any());
    }

    @Test
    void run_emailTakenByNonAdmin_skips() {
        when(users.countByRole(Role.OFFICER_ADMIN)).thenReturn(0L);
        when(users.existsByEmail("admin@uta.edu")).thenReturn(true);

        runner("admin@uta.edu", "adminpass1").run();

        verify(users, never()).save(any());
    }

    @Test
    void run_credentialsMissing_skipsWithoutDbAccess() {
        runner("", null).run();

        verify(users, never()).countByRole(any());
    }

    @Test
    void run_tableMissing_logsAndContinues() {
        when(users.countByRole(Role.OFFICER_ADMIN)).thenThrow(new InvalidDataAccessResourceUsageException("no table"));

        assertThatCode(() -> runner("admin@uta.edu", "adminpass1").run()).doesNotThrowAnyException();
    }
}
