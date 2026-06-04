package net.atos.digital.multitenant.configurations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.atos.digital.multitenant.tenant.TenantContext;
import net.atos.digital.multitenant.tenant.hibernate.schema.SchemaSelector;
import net.atos.digital.multitenant.tenant.properties.TenantConfigurationRegistry;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.hibernate.HibernateException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.MessageFormat;


@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "2m")
@ConditionalOnProperty(prefix = "jpa.shedlock", name = "enabled", havingValue = "true")
public class ShedlockJpaConfig {

    @Bean
    public LockProvider lockProvider(TenantConfigurationRegistry tenantConfigurationRegistry, SchemaSelector schemaSelector, DataSource dataSource) {

        return new JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(new SchemaSelectedMultitenantDataSource(dataSource, schemaSelector, tenantConfigurationRegistry)))
                .usingDbTime() // Works on Postgres, MySQL, MariaDb, MS SQL, Oracle, DB2, HSQL and H2
                .build()
        );
    }

    public static class SchemaSelectedMultitenantDataSource extends DelegatingDataSource {
        private SchemaSelector schemaSelector;
        private TenantConfigurationRegistry tenantConfigurationRegistry;

        public SchemaSelectedMultitenantDataSource(DataSource targetDataSource, SchemaSelector schemaSelector, TenantConfigurationRegistry tenantConfigurationRegistry) {
            super(targetDataSource);
            this.schemaSelector = schemaSelector;
            this.tenantConfigurationRegistry = tenantConfigurationRegistry;
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection connection = super.getConnection();
            TenantContext context = tenantConfigurationRegistry.getTenantContext();
            if (context.getCurrentTenant() != null){
                String schemaName = context.getCurrentSchemaName();
                try (PreparedStatement preparedStatement = connection.prepareStatement(schemaSelector.apply(schemaName))) {
                    preparedStatement.execute();
                } catch (SQLException e) {
                    throw new HibernateException(MessageFormat.format("Could not alter JDBC connection to specified schema [{0}]", schemaName), e);
                }
            }
            return connection;
        }
    }
}
