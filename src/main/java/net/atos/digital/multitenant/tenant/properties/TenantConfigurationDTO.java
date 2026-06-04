package net.atos.digital.multitenant.tenant.properties;

import java.util.*;

public record TenantConfigurationDTO(
        List<TenantData> tenants,
        Map<String, Map<String, Object>> propertiesPerTenant
) {
    public TenantConfigurationDTO {
        tenants = Objects.nonNull(tenants) ? tenants : new ArrayList<>();
        propertiesPerTenant = Objects.nonNull(propertiesPerTenant) ? propertiesPerTenant : new HashMap<>();
    }

    public void putValues(TenantData t, Map<String, Object> values) {
        this.propertiesPerTenant.put(t.tenantName(), values);
    }

}
