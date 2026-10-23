package com.studentorgs.portal.user.config;

import java.time.Clock;
import java.util.Locale;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;
import com.studentorgs.portal.user.domain.AppUser;
import com.studentorgs.portal.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates one active admin from SEED_ADMIN_EMAIL / SEED_ADMIN_PASSWORD when no admin exists.
 * Disabled in tests; skipped with a warning if the table is not there yet.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(SeedAdminProperties.class)
@ConditionalOnProperty(prefix = "orgportal.seed-admin", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeedAdminRunner implements CommandLineRunner {

    private final SeedAdminProperties properties;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Override
    public void run(String... args) {
        if (isBlank(properties.email()) || isBlank(properties.password())) {
            log.info("Seed admin skipped: SEED_ADMIN_EMAIL / SEED_ADMIN_PASSWORD not set");
            return;
        }
        try {
            seed();
        } catch (DataAccessException e) {
            log.warn("Seed admin skipped: app_user is not available yet ({})", e.getClass().getSimpleName());
        }
    }

    void seed() {
        if (users.countByRole(Role.OFFICER_ADMIN) > 0) {
            log.info("Seed admin skipped: an officer_admin already exists");
            return;
        }
        String email = properties.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email)) {
            log.warn("Seed admin skipped: {} already exists as a member", email);
            return;
        }
        String name = isBlank(properties.displayName()) ? "Administrator" : properties.displayName().trim();
        users.save(new AppUser(email, name, passwordEncoder.encode(properties.password()),
                Role.OFFICER_ADMIN, UserStatus.ACTIVE, clock.instant()));
        log.info("Seed admin created: {}", email);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
