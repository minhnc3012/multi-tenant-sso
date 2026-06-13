package com.identityplatform.sdk;

import com.identityplatform.sdk.service.IdpOrgResolver;
import com.identityplatform.sdk.service.IdpUserSyncService;
import com.identityplatform.sdk.token.IdpTokenProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Auto-configures the IDP client beans when {@code idp.client.base-url} is present.
 *
 * <p>To disable in tests: set {@code idp.client.base-url} to empty or add
 * {@code @ConditionalOnProperty} override in test config.
 */
@AutoConfiguration
@EnableConfigurationProperties(IdpProperties.class)
@ConditionalOnProperty(prefix = "idp.client", name = "base-url")
public class IdpClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public WebClient idpWebClient() {
        return WebClient.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public IdpTokenProvider idpTokenProvider(IdpProperties props, WebClient idpWebClient) {
        return new IdpTokenProvider(props, idpWebClient);
    }

    @Bean
    @ConditionalOnMissingBean
    public IdpOrgResolver idpOrgResolver(IdpProperties props,
                                         IdpTokenProvider idpTokenProvider,
                                         WebClient idpWebClient) {
        return new IdpOrgResolver(props, idpTokenProvider, idpWebClient);
    }

    @Bean
    @ConditionalOnMissingBean
    public IdpUserSyncService idpUserSyncService(IdpProperties props,
                                                 IdpTokenProvider tokenProvider,
                                                 WebClient idpWebClient,
                                                 IdpOrgResolver idpOrgResolver) {
        return new IdpUserSyncService(props, tokenProvider, idpWebClient, idpOrgResolver);
    }
}
