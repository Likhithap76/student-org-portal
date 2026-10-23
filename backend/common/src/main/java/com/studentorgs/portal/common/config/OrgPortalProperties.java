package com.studentorgs.portal.common.config;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings shared by every module, bound from {@code orgportal.*}.
 * Defaults live in {@code orgportal-defaults.yml}; secrets come from env vars.
 */
@ConfigurationProperties(prefix = "orgportal")
public record OrgPortalProperties(
        @DefaultValue Security security,
        @DefaultValue Services services,
        @DefaultValue OpenApi openapi,
        /** The organization's time zone, for date rules such as "overdue". */
        @DefaultValue("America/Chicago") ZoneId timeZone) {

    public record Security(
            String jwtSecret,
            @DefaultValue("2h") Duration jwtExpiry,
            String internalToken,
            @DefaultValue List<String> publicPaths) {
    }

    public record Services(
            @DefaultValue("http://localhost:8081") String userUrl,
            @DefaultValue("http://localhost:8082") String eventUrl,
            @DefaultValue("http://localhost:8083") String taskUrl,
            @DefaultValue("http://localhost:8084") String contentUrl,
            @DefaultValue("http://localhost:8085") String notificationUrl,
            @DefaultValue("http://localhost:8086") String reportingUrl,
            @DefaultValue("http://localhost:8087") String aiUrl) {
    }

    public record OpenApi(@DefaultValue("http://localhost:8080/api") String serverUrl) {
    }
}
