package com.studentorgs.portal.common.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.Profiles;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Adds the shared defaults ({@code orgportal-defaults.yml}, plus {@code orgportal-defaults-<profile>.yml}
 * for the active {@code test}, {@code local} or {@code supabase} profile) with the lowest precedence, so every service gets the
 * same datasource, JPA, actuator and logging settings while its own application.yml can still override them.
 */
public class OrgPortalDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String DEFAULTS = "orgportal-defaults.yml";
    static final String TEST_DEFAULTS = "orgportal-defaults-test.yml";
    static final String LOCAL_DEFAULTS = "orgportal-defaults-local.yml";
    static final String SUPABASE_DEFAULTS = "orgportal-defaults-supabase.yml";

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        MutablePropertySources sources = environment.getPropertySources();
        if (environment.acceptsProfiles(Profiles.of("test"))) {
            load(TEST_DEFAULTS).forEach(sources::addLast);
        } else if (environment.acceptsProfiles(Profiles.of("local"))) {
            load(LOCAL_DEFAULTS).forEach(sources::addLast);
        } else if (environment.acceptsProfiles(Profiles.of("supabase"))) {
            load(SUPABASE_DEFAULTS).forEach(sources::addLast);
        }
        load(DEFAULTS).forEach(sources::addLast);
    }

    private List<PropertySource<?>> load(String name) {
        try {
            return loader.load("orgportal:" + name, new ClassPathResource(name));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load " + name, e);
        }
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }
}
