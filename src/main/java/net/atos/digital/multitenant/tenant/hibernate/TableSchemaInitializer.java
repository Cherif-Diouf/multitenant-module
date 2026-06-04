package net.atos.digital.multitenant.tenant.hibernate;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.atos.digital.multitenant.tenant.Tenant;
import net.atos.digital.multitenant.tenant.TenantContext;
import net.atos.digital.multitenant.tenant.properties.TenantConfigurationRegistry;
import org.hibernate.boot.Metadata;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.tool.schema.SourceType;
import org.hibernate.tool.schema.TargetType;
import org.hibernate.tool.schema.internal.ExceptionHandlerLoggedImpl;
import org.hibernate.tool.schema.spi.*;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;

@Slf4j
public class TableSchemaInitializer {
    private final TenantConfigurationRegistry tenantConfigurationRegistry;
    private final TenantContext tenantContext;
    private final EntityManagerFactory entityManagerFactory;

    public TableSchemaInitializer(TenantConfigurationRegistry tenantConfigurationRegistry, TenantContext tenantContext, EntityManagerFactory entityManagerFactory) {
        this.tenantConfigurationRegistry = tenantConfigurationRegistry;
        this.tenantContext = tenantContext;
        this.entityManagerFactory = entityManagerFactory;
    }

    @PostConstruct
    public void init() {
        String ddlAuto = entityManagerFactory.getProperties().get("hibernate.hbm2ddl.auto").toString();
        if ("none".equals(ddlAuto)) {
            log.info("DDL is set to 'none', skipping schema initialization");
        }

        initializeTenantsSchemas();
    }

    private void initializeTenantsSchemas() {
        SessionFactoryImplementor sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
        log.info("info init --------");
        tenantConfigurationRegistry.getTenants().forEach(tenantData -> {
            Tenant tenant = tenantData.toTenant();
            try {
                log.info("Initializing schema for tenant: {}", tenant);
                tenantContext.setCurrentTenant(tenant);

                Metadata metadata = HibernateInfoHolder.getMetadata();

                // Configuration des options d'exécution
                ExecutionOptions executionOptions = createExecutionOptions(sessionFactory);

                // ContributableMatcher - accepte toutes les contributions
                ContributableMatcher contributableMatcher = (contributable) -> true;

                // Configuration de la source
                SourceDescriptor sourceDescriptor = createSourceDescriptor();

                // Configuration des cibles
                TargetDescriptor targetDescriptor = createTargetDescriptor();

                // Utilisation de la vraie signature Hibernate 6
                sessionFactory.getServiceRegistry()
                        .getService(SchemaManagementTool.class)
                        .getSchemaCreator(sessionFactory.getProperties())
                        .doCreation(
                                metadata,
                                executionOptions,
                                contributableMatcher,
                                sourceDescriptor,
                                targetDescriptor
                        );

                log.info("Schema creation completed for tenant: {}", tenant);

            } catch (Exception e) {
                log.error("Error during schema creation for tenant {}: {}", tenant, e.getMessage(), e);
            }
        });

        tenantContext.clear();
        log.info("All tenant schemas initialization completed");
    }

    private ExecutionOptions createExecutionOptions(SessionFactoryImplementor sessionFactory) {
        return new ExecutionOptions() {
            @Override
            public boolean shouldManageNamespaces() {
                return true;
            }

            @Override
            public Map<String, Object> getConfigurationValues() {
                return sessionFactory.getProperties();
            }

            @Override
            public ExceptionHandler getExceptionHandler() {
                return new ExceptionHandlerLoggedImpl();
            }

            @Override
            public SchemaFilter getSchemaFilter() {
                // Retourne un filtre qui accepte tout
                return SchemaFilter.ALL;
            }
        };
    }

    private SourceDescriptor createSourceDescriptor() {
        return new SourceDescriptor() {
            @Override
            public SourceType getSourceType() {
                return SourceType.METADATA;
            }

            @Override
            public ScriptSourceInput getScriptSourceInput() {
                return null; // Pas utilisé quand SourceType = METADATA
            }
        };
    }

    private TargetDescriptor createTargetDescriptor() {
        return new TargetDescriptor() {
            @Override
            public EnumSet<TargetType> getTargetTypes() {
                return EnumSet.of(TargetType.DATABASE, TargetType.STDOUT);
            }

            @Override
            public ScriptTargetOutput getScriptTargetOutput() {
                return new ScriptTargetOutput() {
                    @Override
                    public void prepare() {
                        // Préparation pour l'écriture
                    }

                    @Override
                    public void accept(String command) {
                        // Affichage sur stdout
                        System.out.println(command);
                    }

                    @Override
                    public void release() {
                        // Nettoyage après écriture
                    }
                };
            }
        };
    }
}