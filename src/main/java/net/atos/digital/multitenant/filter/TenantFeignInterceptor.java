

package net.atos.digital.multitenant.filter;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import net.atos.digital.multitenant.tenant.TenantContext;
import net.atos.digital.multitenant.tenant.properties.TenantConfigurationRegistry;
import net.atos.digital.multitenant.utils.ProfileConstants;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile(ProfileConstants.MULTI_SCHEMA_OR_MULTITENANT)
@Component
@ConditionalOnClass(RequestInterceptor.class)
@Slf4j
public class TenantFeignInterceptor implements RequestInterceptor {
    private final TenantContext tenantContext;
    private final TenantConfigurationRegistry tenantConfigurationRegistry;

    public TenantFeignInterceptor(TenantContext tenantContext, TenantConfigurationRegistry tenantConfigurationRegistry) {
        this.tenantContext = tenantContext;
        this.tenantConfigurationRegistry = tenantConfigurationRegistry;
    }

    @Override
    public void apply(RequestTemplate requestTemplate) {
        log.info("feign interceptor - currentTenant: {}, isMultitenantActive: {}, isMultiSchemaActive: {}", tenantContext.getCurrentTenant(), tenantConfigurationRegistry.isMultitenantActive(), tenantConfigurationRegistry.isMultiSchemaActive());
        if (tenantContext.getCurrentTenant() != null) {
            log.info("feign interceptor - tenantName: {}", tenantContext.getCurrentTenantName());
            requestTemplate.header(ProfileConstants.X_TENANT, tenantContext.getCurrentTenantName());
        }
    }
}