package com.studentorgs.portal.common.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

/**
 * OpenAPI metadata for each service: a bearer-JWT scheme, and the gateway's {@code /api} as server,
 * so "Try it out" in the aggregated Swagger UI goes through the gateway.
 */
@AutoConfiguration(after = CoreAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(OpenAPI.class)
public class OpenApiAutoConfiguration {

    private static final String BEARER = "bearerAuth";

    @Bean
    @ConditionalOnMissingBean
    OpenAPI orgPortalOpenApi(OrgPortalProperties properties,
            @Value("${spring.application.name:orgportal}") String appName) {
        return new OpenAPI()
                .info(new Info().title("Org Portal - " + appName).version("v1"))
                .servers(List.of(new Server().url(properties.openapi().serverUrl())))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
