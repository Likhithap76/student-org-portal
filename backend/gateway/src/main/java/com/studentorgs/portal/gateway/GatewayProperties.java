package com.studentorgs.portal.gateway;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param publicPaths    path patterns reachable without a JWT
 * @param allowedOrigins frontend origins allowed by CORS
 */
@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(
        @DefaultValue List<String> publicPaths,
        @DefaultValue List<String> allowedOrigins) {
}
