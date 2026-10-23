package com.studentorgs.portal.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** First admin account, from SEED_ADMIN_EMAIL / SEED_ADMIN_PASSWORD. */
@ConfigurationProperties(prefix = "orgportal.seed-admin")
public record SeedAdminProperties(boolean enabled, String email, String password, String displayName) {

    @Override
    public String toString() {
        return "SeedAdminProperties[enabled=" + enabled + ", email=" + email + "]";
    }
}
