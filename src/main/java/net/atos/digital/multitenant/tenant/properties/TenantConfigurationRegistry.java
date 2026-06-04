

package net.atos.digital.multitenant.tenant.properties;

import lombok.Getter;
import lombok.Setter;
import net.atos.digital.multitenant.TenantException;
import net.atos.digital.multitenant.tenant.Tenant;
import net.atos.digital.multitenant.tenant.TenantContext;
import org.springframework.core.env.Environment;

import java.util.*;

import static net.atos.digital.multitenant.tenant.TenantContext.DEFAULT_TENANT_UUID;

public class TenantConfigurationRegistry {
    @Getter
    @Setter
    private TenantContext tenantContext;
    private final Environment environment;

    @Getter
    @Setter
    protected List<TenantData> tenants = new ArrayList<>();
    @Setter
    @Getter
    protected Map<String, Map<String, Object>> propertiesPerTenant = new HashMap<>();

    @Getter
    @Setter
    Map<String, Map<Class<?>, Object>> configs = new HashMap<>();
    @Getter
    @Setter
    private boolean multitenantActive;

    @Getter
    @Setter
    private boolean multiSchemaActive;

    public TenantConfigurationRegistry(TenantContext tenantContext, Environment environment) {
        this.tenantContext = tenantContext;
        this.environment = environment;
    }

    public <T> T get(Class<T> clazz) {
        if (multitenantActive && tenantContext.getCurrentTenant() != null) {
            return (T) configs.get(tenantContext.getCurrentTenant().name()).get(clazz);
        }
        return (T) configs.get(DEFAULT_TENANT_UUID.toString()).get(clazz);
    }
    public <T> T get(Class<T> clazz, Tenant tenant){
        if(multitenantActive){
            return (T) configs.get(tenant.name()).get(clazz);
        }else{
            return (T) configs.get(DEFAULT_TENANT_UUID.toString()).get(clazz);
        }
    }

    public Object getValue(final String key) {
        if (multitenantActive) {
            Tenant currentTenant = tenantContext.getCurrentTenant();
            if (currentTenant == null) {
                throw new TenantException("No tenant set");
            }
            Map<String, Object> propertyValues = propertiesPerTenant.get(currentTenant.name());
            if (propertyValues == null) {
                throw new TenantException("No property " + key + " for tenant " + currentTenant.name());
            }
            return propertiesPerTenant.get(currentTenant.name()).get(key);
        } else {
            return this.environment.getProperty(key);
        }
    }

    public Optional<Object> getValue(final String tenantName, final String key) {
        TenantData currentTenant = this.findTenantByName(tenantName);
        if (currentTenant == null) {
            throw new TenantException("Tenant" + tenantName + " does not exist");
        }
        Map<String, Object> propertyValues = propertiesPerTenant.get(currentTenant.tenantName());
        if (propertyValues == null) {
            throw new TenantException("No properties for tenant " + currentTenant.tenantName());
        }
        return Optional.ofNullable(propertiesPerTenant.get(currentTenant.tenantName()).get(key));
    }


    public TenantData findTenantByName(String tenantName) {
        for (TenantData tenant : tenants) {
            if (tenant.tenantName().equals(tenantName)) {
                return tenant;
            }
        }
        throw new TenantException("Tenant with name " + tenantName + " does not exist");
    }

    public TenantData findTenantByUuid(UUID tenantUuid) {
        for (TenantData tenant : tenants) {
            if (tenant.uuid().equals(tenantUuid)) {
                return tenant;
            }
        }
        throw new TenantException("Tenant with Uuid " + tenantUuid + " does not exist");
    }

    public boolean isMultiSchemaActive() {
        return multiSchemaActive || multitenantActive;
    }

}