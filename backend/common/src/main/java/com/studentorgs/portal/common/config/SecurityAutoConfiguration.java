package com.studentorgs.portal.common.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.studentorgs.portal.common.security.InternalTokenFilter;
import com.studentorgs.portal.common.security.JwtAuthenticationFilter;
import com.studentorgs.portal.common.security.JwtService;
import com.studentorgs.portal.common.security.ProblemSecurityHandler;

/**
 * Stateless JWT security for every servlet service. Each service validates the token itself.
 * Open: health, Swagger and {@code orgportal.security.public-paths}. {@code /internal/**} needs
 * the internal token. Everything else needs a valid JWT; finer rules use {@code @PreAuthorize}.
 */
@AutoConfiguration(after = CoreAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(SecurityFilterChain.class)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityAutoConfiguration {

    static final List<String> ALWAYS_PUBLIC = List.of(
            "/actuator/health", "/actuator/health/**", "/actuator/info",
            "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**",
            "/error");

    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("ROLE_OFFICER_ADMIN > ROLE_MEMBER");
    }

    @Bean
    static MethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setRoleHierarchy(roleHierarchy);
        return handler;
    }

    @Bean
    @ConditionalOnMissingBean
    ProblemSecurityHandler problemSecurityHandler(ObjectMapper objectMapper) {
        return new ProblemSecurityHandler(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    SecurityFilterChain orgPortalSecurityFilterChain(HttpSecurity http, OrgPortalProperties properties,
            JwtService jwtService, ProblemSecurityHandler problemHandler) throws Exception {
        List<String> publicPaths = new ArrayList<>(ALWAYS_PUBLIC);
        publicPaths.addAll(properties.security().publicPaths());

        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(problemHandler).accessDeniedHandler(problemHandler))
                .addFilterBefore(new InternalTokenFilter(properties.security().internalToken()),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(publicPaths.toArray(String[]::new)).permitAll()
                        .requestMatchers("/internal/**").hasAuthority(InternalTokenFilter.INTERNAL_AUTHORITY)
                        .anyRequest().authenticated());
        return http.build();
    }
}
