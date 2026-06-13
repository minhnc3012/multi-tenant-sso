package com.identityplatform.authserver.config;

import com.identityplatform.core.tenant.TenantAwareDataSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    /**
     * Wraps the Hikari DataSource auto-configured by Spring Boot with TenantAwareDataSource.
     * Uses a static @Bean BeanPostProcessor so it's registered early (before DataSource is
     * injected into EntityManagerFactory / TransactionManager) without triggering eager
     * initialization of the DataSourceConfig class itself.
     */
    @Bean
    static BeanPostProcessor tenantAwareDataSourceWrapper() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if ("dataSource".equals(beanName)
                        && bean instanceof DataSource ds
                        && !(bean instanceof TenantAwareDataSource)) {
                    return new TenantAwareDataSource(ds);
                }
                return bean;
            }
        };
    }
}
