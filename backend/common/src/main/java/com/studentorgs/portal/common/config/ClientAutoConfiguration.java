package com.studentorgs.portal.common.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

import com.studentorgs.portal.common.client.InternalRestClientFactory;
import com.studentorgs.portal.common.client.NotificationClient;
import com.studentorgs.portal.common.client.UserDirectoryClient;
import com.studentorgs.portal.common.security.MemberGuard;

/** RestClient plumbing for {@code /internal/**} calls between servlet services. */
@AutoConfiguration(after = {CoreAutoConfiguration.class, RestClientAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(RestClient.class)
public class ClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    InternalRestClientFactory internalRestClientFactory(RestClient.Builder builder, OrgPortalProperties properties) {
        return new InternalRestClientFactory(builder, properties.security().internalToken());
    }

    @Bean
    @ConditionalOnMissingBean
    NotificationClient notificationClient(InternalRestClientFactory factory, OrgPortalProperties properties) {
        return new NotificationClient(factory.create(properties.services().notificationUrl()));
    }

    @Bean
    @ConditionalOnMissingBean
    UserDirectoryClient userDirectoryClient(InternalRestClientFactory factory, OrgPortalProperties properties) {
        return new UserDirectoryClient(factory.create(properties.services().userUrl()));
    }

    @Bean
    @ConditionalOnMissingBean
    MemberGuard memberGuard(UserDirectoryClient userDirectoryClient) {
        return new MemberGuard(userDirectoryClient);
    }
}
