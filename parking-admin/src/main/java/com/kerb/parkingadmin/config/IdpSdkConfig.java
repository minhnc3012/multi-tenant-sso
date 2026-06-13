package com.kerb.parkingadmin.config;

import com.identityplatform.sdk.IdpProperties;
import com.identityplatform.sdk.service.IdpOrgResolver;
import com.identityplatform.sdk.service.IdpUserSyncService;
import com.identityplatform.sdk.token.IdpTokenProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Explicitly wires the idp-client-sdk beans into the parking-admin context.
 * This bypasses Spring Boot autoconfigure entirely, which is more reliable
 * when the SDK is used as a local Maven dependency.
 */
@Configuration
@EnableConfigurationProperties(IdpProperties.class)
public class IdpSdkConfig {

    @Bean
    public WebClient idpWebClient() {
        return WebClient.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
    }

    @Bean
    public IdpTokenProvider idpTokenProvider(IdpProperties props, WebClient idpWebClient) {
        return new IdpTokenProvider(props, idpWebClient);
    }

    @Bean
    public IdpOrgResolver idpOrgResolver(IdpProperties props,
                                         IdpTokenProvider idpTokenProvider,
                                         WebClient idpWebClient) {
        return new IdpOrgResolver(props, idpTokenProvider, idpWebClient);
    }

    @Bean
    public IdpUserSyncService idpUserSyncService(IdpProperties props,
                                                 IdpTokenProvider tokenProvider,
                                                 WebClient idpWebClient,
                                                 IdpOrgResolver idpOrgResolver) {
        return new IdpUserSyncService(props, tokenProvider, idpWebClient, idpOrgResolver);
    }
}
