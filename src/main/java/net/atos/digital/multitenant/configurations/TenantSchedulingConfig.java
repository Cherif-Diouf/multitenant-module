

package net.atos.digital.multitenant.configurations;

import lombok.extern.slf4j.Slf4j;
import net.atos.digital.multitenant.tenant.TenantContext;
import net.atos.digital.multitenant.tenant.TenantService;
import net.atos.digital.multitenant.tenant.properties.TenantConfigurationRegistry;
import net.atos.digital.multitenant.tenant.scheduling.TenantScheduledAnnotationBeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@Configuration
@ConditionalOnClass(Scheduled.class)
public class TenantSchedulingConfig {

    @Bean
    @DependsOn("tenantConfigurationRegistrar")
    public TenantScheduledAnnotationBeanPostProcessor tenantScheduledAnnotationBeanPostProcessor(TenantConfigurationRegistry tenantConfigurationRegistry, TenantContext tenantContext, TenantService tenantService) {
        return new TenantScheduledAnnotationBeanPostProcessor(tenantConfigurationRegistry, tenantService, tenantContext);
    }
}