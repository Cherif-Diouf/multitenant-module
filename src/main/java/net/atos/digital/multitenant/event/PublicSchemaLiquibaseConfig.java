package net.atos.digital.multitenant.event;

import liquibase.integration.spring.MultiTenantSpringLiquibase;
import liquibase.integration.spring.SpringLiquibase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@DependsOn("multiTenantSpringLiquibase")
@ConditionalOnClass(SpringLiquibase.class)
@ConditionalOnProperty(prefix = "spring.liquibase", name = "enabled")
public class PublicSchemaLiquibaseConfig {

    private final ObjectProvider<MultiTenantSpringLiquibase> liquibaseProvider;
    @Value("${multitenant.public-schema.name:public}")
    private String publicSchema;

    @Value("${multitenant.public-schema.changelog:classpath:/db/changelog/multitenant-changelog.xml}")
    private String publicChangelogPath;

    @EventListener(ApplicationReadyEvent.class)
    public void runPublicSchemaMigrations() {
        log.info("Application ready. Starting migrations for public schema...");

        liquibaseProvider.ifAvailable(liquibase -> {
            log.info("Running Liquibase migration for schema: {}", publicSchema);
            try {
                liquibase.setSchemas(List.of(publicSchema));
                liquibase.setChangeLog(publicChangelogPath);
                liquibase.afterPropertiesSet();
                log.info("Liquibase migration completed successfully.");
            } catch (Exception e) {
                throw new RuntimeException("Liquibase migration failed for schema: " + publicSchema, e);
            }

        });
    }

}