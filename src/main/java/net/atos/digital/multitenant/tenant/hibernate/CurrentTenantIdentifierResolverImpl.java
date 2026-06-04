

package net.atos.digital.multitenant.tenant.hibernate;

import net.atos.digital.multitenant.tenant.TenantContext;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

public class CurrentTenantIdentifierResolverImpl implements CurrentTenantIdentifierResolver {
    private final TenantContext tenantContext;

    public CurrentTenantIdentifierResolverImpl(TenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    @Override
    public String resolveCurrentTenantIdentifier() {
        if (tenantContext.getCurrentTenant() == null) {
            return "BOOTSTRAP";
        }
        return tenantContext.getCurrentSchemaName();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
