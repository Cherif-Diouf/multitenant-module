package net.atos.digital.multitenant.tenant.provisioning;

import liquibase.integration.spring.SpringLiquibase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.atos.digital.multitenant.tenant.Tenant;
import net.atos.digital.multitenant.tenant.TenantContext;
import net.atos.digital.multitenant.tenant.hibernate.schema.SchemaAdmin;
import net.atos.digital.multitenant.tenant.properties.TenantConfigurationRegistry;
import net.atos.digital.multitenant.tenant.properties.TenantData;
import net.atos.digital.multitenant.utils.ProfileConstants;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Implémentation du provisioning d'un tenant à chaud.
 * <p>
 * Étapes :
 * <ol>
 *   <li>Validation du nom de schéma (alphanumeric + underscore, lowercase)</li>
 *   <li>CREATE SCHEMA IF NOT EXISTS (hors transaction pour visibilité immédiate)</li>
 *   <li>Exécution des migrations Liquibase sur le nouveau schéma</li>
 *   <li>Enregistrement du tenant dans le TenantConfigurationRegistry</li>
 *   <li>Positionnement du TenantContext pour les opérations BDD suivantes</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnClass(SpringLiquibase.class)
@Profile(ProfileConstants.MULTI_SCHEMA_OR_MULTITENANT)
public class DefaultTenantProvisioningService implements TenantProvisioningService {

    private static final Pattern VALID_SCHEMA_NAME = Pattern.compile("^[0-9a-z_]+$");

    private final SchemaAdmin schemaAdmin;
    private final DataSourceProperties dataSourceProperties;
    private final LiquibaseProperties liquibaseProperties;
    private final TenantConfigurationRegistry tenantConfigurationRegistry;
    private final TenantContext tenantContext;

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Tenant provision(String tenantId, String tenantName) {
        String schemaName = tenantName.toLowerCase();
        validateSchemaName(schemaName);

        // 1. Créer le schéma PostgreSQL
        log.info("Provisioning schema '{}'...", schemaName);
        schemaAdmin.createSchemaIfNotExists(schemaName);

        // 2. Exécuter Liquibase
        log.info("Running Liquibase migrations on schema '{}'...", schemaName);
        runLiquibase(schemaName);

        // 3. Enregistrer le tenant dans le registry
        UUID uuid = UUID.fromString(tenantId);
        TenantData tenantData = new TenantData(uuid, schemaName, tenantName, true);
        tenantConfigurationRegistry.getTenants().add(tenantData);
        tenantConfigurationRegistry.getPropertiesPerTenant()
                .putIfAbsent(schemaName, new HashMap<>());

        // 4. Positionner le TenantContext
        Tenant tenant = tenantData.toTenant();
        tenantContext.setCurrentTenant(tenant);

        log.info("Schema '{}' provisioned successfully.", schemaName);
        return tenant;
    }

    @Override
    public void clearContext() {
        tenantContext.clear();
    }

    private void runLiquibase(String schemaName) {
        try {
            SpringLiquibase liquibase = new SpringLiquibase();
            liquibase.setDataSource(createSchemaDataSource(schemaName));
            liquibase.setChangeLog(liquibaseProperties.getChangeLog());
            liquibase.setShouldRun(true);
            liquibase.afterPropertiesSet();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Liquibase migration failed for schema: " + schemaName, e);
        }
    }

    private DataSource createSchemaDataSource(String schemaName) {
        String url = dataSourceProperties.getUrl();
        String separator = url.contains("?") ? "&" : "?";
        String schemaUrl = url + separator + "currentSchema=" + schemaName;

        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(schemaUrl);
        ds.setUsername(dataSourceProperties.getUsername());
        ds.setPassword(dataSourceProperties.getPassword());
        return ds;
    }

    private void validateSchemaName(String schemaName) {
        if (schemaName == null || schemaName.isBlank()) {
            throw new IllegalArgumentException("Schema name must not be null/blank");
        }
        if (!VALID_SCHEMA_NAME.matcher(schemaName).matches()) {
            throw new IllegalArgumentException(
                    "Schema name is malformed: " + schemaName + " (must be lowercase alphanumeric/underscore)");
        }
    }
}
