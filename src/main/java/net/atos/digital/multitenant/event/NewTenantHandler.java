/*
 *
 *  * Copyright © 2023 Khoutech. All Rights Reserved.
 *
 */

package net.atos.digital.multitenant.event;

import liquibase.integration.spring.MultiTenantSpringLiquibase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.atos.digital.multitenant.tenant.hibernate.DatabaseSchemaInitializer;
import net.atos.digital.multitenant.tenant.hibernate.schema.SchemaCreatorService;
import net.atos.digital.multitenant.tenant.properties.TenantData;
import net.atos.digital.multitenant.tenant.scheduling.TenantScheduledAnnotationBeanPostProcessor;
import net.atos.digital.multitenant.utils.ProfileConstants;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile(ProfileConstants.MULTI_SCHEMA_OR_MULTITENANT)
@Slf4j
@RequiredArgsConstructor
public class NewTenantHandler {

    private final ObjectProvider<DatabaseSchemaInitializer> schemaInitializerProvider;
    private final ObjectProvider<TenantScheduledAnnotationBeanPostProcessor> tenantScheduledAnnotationBeanPostProcessorProvider;
    private final SchemaCreatorService schemaCreatorService;
    private final ApplicationContext applicationContext;

    @EventListener
    public void handleContextStart(NewTenantsEvent event) {
        log.info("Starting to provision new tenants based on NewTenantsEvent.");
        try {
            List<TenantData> newTenants = event.getTenants();
            updateScheduler(newTenants);
            initializeDatabaseSchemas(newTenants);
            runLiquibaseMigrations(newTenants);

            log.info("Successfully finished provisioning new tenants.");

        } catch (Exception e) {
            log.error("A critical error occurred while handling new tenants. Process aborted.", e);
            throw new IllegalStateException("Failed to provision new tenants", e);
        }
    }

    private void updateScheduler(List<TenantData> tenantData) {
        tenantScheduledAnnotationBeanPostProcessorProvider
                .ifAvailable(tenantScheduledAnnotationBeanPostProcessor -> tenantScheduledAnnotationBeanPostProcessor.multitenantProcessScheduled(tenantData));
    }

    /**
     * Initializes the database schemas for the new tenants if the {@link DatabaseSchemaInitializer} is available.
     *
     * @param newTenants      The list of new tenants.
     */
    private void initializeDatabaseSchemas(List<TenantData> newTenants) {
        schemaInitializerProvider.ifAvailable(schemaInitializer -> {
            List<String> schemasToCreate = schemaCreatorService.getAllSchemas(newTenants);
            if (CollectionUtils.isNotEmpty(schemasToCreate)) {
                log.info("Initializing {} database schemas...", schemasToCreate.size());
                schemaInitializer.createTenantsSchemas(schemasToCreate);
            }
        });
    }

    /**
     * Runs Liquibase migrations for the new tenants if {@link MultiTenantSpringLiquibase} is available.
     *
     * @param newTenants      The list of new tenants.
     */
    private void runLiquibaseMigrations(List<TenantData> newTenants) {
        if (applicationContext.containsBean("multiTenantSpringLiquibase")) {
            MultiTenantSpringLiquibase liquibase = applicationContext.getBean(MultiTenantSpringLiquibase.class);
            LiquibaseProperties liquibaseProperties = applicationContext.getBean(LiquibaseProperties.class);
            List<String> schemasToMigrate = schemaCreatorService.getAllSchemasLiquibase(newTenants);
            if (CollectionUtils.isNotEmpty(schemasToMigrate)) {
                log.info("Running Liquibase migration for {} schemas: {}", schemasToMigrate.size(), schemasToMigrate);
                try {
                    liquibase.setSchemas(schemasToMigrate);
                    liquibase.setChangeLog(liquibaseProperties.getChangeLog());
                    liquibase.afterPropertiesSet();
                    log.info("Liquibase migration completed successfully.");
                } catch (Exception e) {
                    throw new RuntimeException("Liquibase migration failed for schemas: " + schemasToMigrate, e);
                }
            }
        }
    }

}