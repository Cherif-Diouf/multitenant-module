package net.atos.digital.multitenant.tenant.hibernate.schema;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import net.atos.digital.multitenant.properties.SchemaConfigurationProperties;
import net.atos.digital.multitenant.tenant.Tenant;
import net.atos.digital.multitenant.tenant.properties.TenantConfigurationRegistry;
import net.atos.digital.multitenant.tenant.properties.TenantData;
import net.atos.digital.multitenant.utils.ProfileConstants;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
@Profile(ProfileConstants.MULTI_SCHEMA_OR_MULTITENANT)
public class SchemaCreatorService {


    final TenantConfigurationRegistry tenantConfigurationRegistry;
    final SchemaConfigurationProperties schemaConfigurationProperties;

    public List<String> getAllSchemas() {
        return new ArrayList<>(collectSchemas(false, tenantConfigurationRegistry.getTenants()));
    }

    public List<String> getAllSchemasLiquibase() {
        return new ArrayList<>(collectSchemas(true, tenantConfigurationRegistry.getTenants()));
    }

    public List<String> getAllSchemas(List<TenantData> tenantDatas) {
        return new ArrayList<>(collectSchemas(false, tenantDatas));
    }

    public List<String> getAllSchemasLiquibase(List<TenantData> tenantDatas) {
        return new ArrayList<>(collectSchemas(true, tenantDatas));
    }

    public List<String> getAllUpcomingSchemas() {
        return new ArrayList<>();
    }


    /**
     * Collect schemas from:
     * 1) Static configuration (filtered by liquibaseEnabled when liquibaseOnly = true)
     * 2) Tenants (always included; liquibaseEnabled does not apply at tenant level)
     * 3) In non-multitenant mode, resolve the default schema by searching tenants first
     *    (so school-year and naming rules are applied), otherwise fallback to configured value.
     *
     * Uses LinkedHashSet to preserve insertion order and deduplicate.
     */
    private Set<String> collectSchemas(boolean liquibaseOnly, List<TenantData> tenantDatas) {
        Set<String> schemas = new LinkedHashSet<>();
        List<SchemaConfigurationProperties.ItemSchemaConfiguration> configuredSchemas =
                schemaConfigurationProperties.getSchemas() != null ? schemaConfigurationProperties.getSchemas() : Collections.emptyList();

        // 1) Static schemas from configuration
        configuredSchemas.forEach(item -> {
            if (!liquibaseOnly || item.liquibaseEnabled()) {
                schemas.add(item.schemaName());
            }
        });

        // 2) Tenant-derived schemas
        if (tenantConfigurationRegistry.isMultitenantActive()) {
            tenantDatas.forEach(tenantData -> {
                Tenant tenant = tenantData.toTenant();
                schemas.add(tenant.schemaName());
            });
        } else {
            // 3) Non-multitenant: resolve default schema via tenant lookup if possible
            SchemaConfigurationProperties.ItemSchemaConfiguration defaultCfg = schemaConfigurationProperties.getDefaultSchema();
            if (defaultCfg != null && (!liquibaseOnly || defaultCfg.liquibaseEnabled())) {
                String resolvedDefault = resolveDefaultSchemaNameFromTenants();
                schemas.add(resolvedDefault);
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Collected schemas (liquibaseOnly={}): {}", liquibaseOnly, schemas);
        }
        return schemas;
    }



        /**
         * Resolve the default schema by looking up a matching tenant:
         * - The configured value may represent either a tenantName or an already-final schemaName.
         * - If a tenant matches (by tenantName or computed schemaName), return the computed schemaName
         *   (so school-year and other naming rules apply). Otherwise, return the configured value as-is.
         */
    private String resolveDefaultSchemaNameFromTenants() {
        SchemaConfigurationProperties.ItemSchemaConfiguration defaultSchema = schemaConfigurationProperties.getDefaultSchema();
        if (defaultSchema == null) {
            return "public";
        }
        String configured = defaultSchema.schemaName();

        return tenantConfigurationRegistry.getTenants().stream()
                .map(TenantData::toTenant)
                .filter(t -> configured.equals(t.name()))
                .map(Tenant::schemaName)
                .findFirst()
                .orElse(configured);
    }

}