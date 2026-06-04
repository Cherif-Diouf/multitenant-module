

package net.atos.digital.multitenant.configurations;

import net.atos.digital.multitenant.tenant.TenantContext;
import net.atos.digital.multitenant.tenant.cache.MultitenantKeyGenerator;
import net.atos.digital.multitenant.tenant.properties.TenantConfigurationRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(CacheManager.class)
public class TenantCacheConfig {

    @Bean("multitenantKeyGenerator")
    public KeyGenerator multitenantKeyGenerator(TenantContext tenantContext, TenantConfigurationRegistry tenantConfigurationRegistry) {
        return new MultitenantKeyGenerator(tenantContext, tenantConfigurationRegistry);
    }
}
