package com.studentorgs.portal.common.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.studentorgs.portal.common.security.JwtService;

/** Beans every module needs, including the reactive gateway: properties, clock, JWT. */
@AutoConfiguration
@EnableConfigurationProperties(OrgPortalProperties.class)
public class CoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    JwtService jwtService(OrgPortalProperties properties, Clock clock) {
        return new JwtService(properties.security().jwtSecret(), properties.security().jwtExpiry(), clock);
    }
}
