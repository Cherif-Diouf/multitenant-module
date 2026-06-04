package net.atos.digital.multitenant.tenant;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import net.atos.digital.multitenant.properties.SchemaConfigurationProperties;
import net.atos.digital.multitenant.tenant.properties.TenantConfigurationRegistry;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TenantService {

    final TenantConfigurationRegistry tenantConfigurationRegistry;
    final SchemaConfigurationProperties schemaConfigurationProperties;

    public List<Tenant> getAllAvailableTenants() {
        List<Tenant> tenants = new ArrayList<>();
        if (tenantConfigurationRegistry.isMultiSchemaActive()) {
            tenantConfigurationRegistry.getTenants().forEach(tenantData -> {
               var tenant = tenantData.toTenant();
               tenants.add(tenant);
            });
            if (CollectionUtils.isNotEmpty(schemaConfigurationProperties.getSchemas())) {
                schemaConfigurationProperties.getSchemas().forEach(itemSchemaConfiguration -> {
                    var tenant = new Tenant(TenantContext.DEFAULT_TENANT_UUID, itemSchemaConfiguration.schemaName(), itemSchemaConfiguration.schemaName());
                    tenants.add(tenant);
                });
            }
            if (tenantConfigurationRegistry.getTenants().stream().noneMatch(tenantData -> tenantData.tenantName().equals(schemaConfigurationProperties.getDefaultSchema().schemaName()))){
                String tenantName = schemaConfigurationProperties.getDefaultSchema().schemaName();
                var tenant = new Tenant(TenantContext.DEFAULT_TENANT_UUID, tenantName, tenantName);
                tenants.add(tenant);
            }
        }
        return tenants;
    }

    public List<Tenant> getSchedulableTenants() {
        List<Tenant> tenants = getAllAvailableTenants();
        if (!schemaConfigurationProperties.isIncludeDefaultSchemaInScheduling()) {
            String defaultSchemaName = schemaConfigurationProperties.getDefaultSchema().schemaName();
            tenants.removeIf(t -> t.schemaName().equals(defaultSchemaName));
        }
        return tenants;
    }
}