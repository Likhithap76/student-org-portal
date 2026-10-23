package com.studentorgs.portal.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.StandardEnvironment;

class OrgPortalDefaultsEnvironmentPostProcessorTest {

    private static final String PREPARE_THRESHOLD = "spring.datasource.hikari.data-source-properties.prepareThreshold";

    private StandardEnvironment environmentWith(String... profiles) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles(profiles);
        new OrgPortalDefaultsEnvironmentPostProcessor().postProcessEnvironment(environment, new SpringApplication());
        return environment;
    }

    @Test
    void supabaseProfile_disablesServerPreparedStatements() {
        assertThat(environmentWith("supabase").getProperty(PREPARE_THRESHOLD)).isEqualTo("0");
        assertThat(environmentWith("supabase").getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("none");
    }

    @Test
    void localProfile_usesSharedH2WithoutPostgresSettings() {
        StandardEnvironment local = environmentWith("local");

        assertThat(local.getProperty(PREPARE_THRESHOLD)).isNull();
        assertThat(local.getProperty("spring.datasource.driver-class-name")).isEqualTo("org.h2.Driver");
    }

    @Test
    void testProfile_usesInMemoryH2() {
        assertThat(environmentWith("test").getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("create-drop");
        assertThat(environmentWith("test").getProperty(PREPARE_THRESHOLD)).isNull();
    }
}
