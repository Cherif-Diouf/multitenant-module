

package net.atos.digital.multitenant.configurations;

import liquibase.integration.spring.MultiTenantSpringLiquibase;
import liquibase.integration.spring.SpringLiquibase;
import lombok.extern.slf4j.Slf4j;
import net.atos.digital.multitenant.properties.SchemaConfigurationProperties;
import net.atos.digital.multitenant.tenant.Tenant;
import net.atos.digital.multitenant.tenant.hibernate.schema.SchemaCreatorService;
import net.atos.digital.multitenant.tenant.properties.TenantConfigurationRegistry;
import net.atos.digital.multitenant.tenant.properties.TenantData;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;
import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Configuration
@ConditionalOnClass(SpringLiquibase.class)
@ConditionalOnProperty(prefix = "spring.liquibase", name = "enabled")
@EnableConfigurationProperties({ DataSourceProperties.class, LiquibaseProperties.class })
@Import({ TenantLiquibaseConfig.TenantLiquibaseEntityManagerFactoryDependsOnPostProcessor.class })
public class TenantLiquibaseConfig {

    @Bean
    @DependsOn("databaseSchemaInitializer")
    public MultiTenantSpringLiquibase multiTenantSpringLiquibase(DataSource dataSource,
                                                                 LiquibaseProperties properties,
                                                                 TenantConfigurationRegistry tenantConfigurationRegistry, SchemaCreatorService schemaCreatorService){
        MultiTenantSpringLiquibase multiTenantSpringLiquibase = new MultiTenantSpringLiquibase();

        multiTenantSpringLiquibase.setDataSource(dataSource);
        multiTenantSpringLiquibase.setChangeLog(properties.getChangeLog());
        multiTenantSpringLiquibase.setClearCheckSums(properties.isClearChecksums());
        multiTenantSpringLiquibase.setContexts(
                properties.getContexts() != null && !properties.getContexts().isEmpty()
                        ? String.join(",", properties.getContexts())
                        : ""
        );
        multiTenantSpringLiquibase.setDefaultSchema(properties.getDefaultSchema());
        multiTenantSpringLiquibase.setLiquibaseSchema(properties.getLiquibaseSchema());
        multiTenantSpringLiquibase.setLiquibaseTablespace(properties.getLiquibaseTablespace());
        multiTenantSpringLiquibase.setDatabaseChangeLogTable(properties.getDatabaseChangeLogTable());
        multiTenantSpringLiquibase.setDatabaseChangeLogLockTable(properties.getDatabaseChangeLogLockTable());
        multiTenantSpringLiquibase.setDropFirst(properties.isDropFirst());
        multiTenantSpringLiquibase.setShouldRun(properties.isEnabled());
        //multiTenantSpringLiquibase.setLabels(properties.getLabels());
        multiTenantSpringLiquibase.setRollbackFile(properties.getRollbackFile());
        List<String> schemas = schemaCreatorService.getAllSchemasLiquibase();
        log.info("schemas liquibase ------------------- {}", schemas);
        multiTenantSpringLiquibase.setSchemas(schemas);
        return multiTenantSpringLiquibase;
    }

    @ConditionalOnClass(LocalContainerEntityManagerFactoryBean.class)
    static class TenantLiquibaseEntityManagerFactoryDependsOnPostProcessor
        extends EntityManagerFactoryDependsOnPostProcessor {

        TenantLiquibaseEntityManagerFactoryDependsOnPostProcessor() {
            super(MultiTenantSpringLiquibase.class);
        }

    }
}