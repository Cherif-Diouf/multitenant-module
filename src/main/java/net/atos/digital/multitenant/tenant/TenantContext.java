

package net.atos.digital.multitenant.tenant;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Slf4j
public class TenantContext {
    public final static UUID DEFAULT_TENANT_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final ThreadLocal<Tenant> currentTenant = new InheritableThreadLocal<>();

    public Tenant getCurrentTenant() {
        return currentTenant.get();
    }

    public String getCurrentTenantName() {
        if (currentTenant.get() != null) {
            return currentTenant.get().name();
        }
        return "";
    }

    public String getCurrentSchemaName() {
        if (currentTenant.get() != null) {
            log.info("schema name --------------- {}", currentTenant.get().schemaName());
            return currentTenant.get().schemaName();
        }
        return "";
    }

    public void setCurrentTenant(Tenant tenant) {
        currentTenant.set(tenant);
        MDC.put("tenant", tenant != null ? tenant.name() : "");
    }

    public void clear() {
        currentTenant.remove();
        MDC.clear();
    }

    public String getTenantCollectionNameForMongo(Tenant tenant, String collectionName) {
        if (tenant != null && StringUtils.hasLength(collectionName)) {
            return collectionName + getPrefixCollectionNameMongo(tenant);
        }
        return "";
    }

    private String getPrefixCollectionNameMongo(Tenant tenant) {
        return "_" + tenant.name();
    }

}