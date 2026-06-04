package net.atos.digital.multitenant.tenant.properties;

import net.atos.digital.multitenant.tenant.Tenant;
import java.util.UUID;

public record TenantData(
        UUID uuid,
        String tenantName,
        String displayName,
        boolean active
) {
    public Tenant toTenant() {
        return new Tenant(uuid, tenantName, tenantName.toLowerCase());
    }
}