

package net.atos.digital.multitenant.configurations;

import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import net.atos.digital.multitenant.properties.SchemaConfigurationProperties;
import net.atos.digital.multitenant.tenant.TenantContext;
import net.atos.digital.multitenant.tenant.hibernate.CurrentTenantIdentifierResolverImpl;
import net.atos.digital.multitenant.tenant.hibernate.DatabaseSchemaInitializer;
import net.atos.digital.multitenant.tenant.hibernate.SchemaBasedMultiTenantConnectionProvider;
import net.atos.digital.multitenant.tenant.hibernate.TableSchemaInitializer;
import net.atos.digital.multitenant.tenant.hibernate.schema.SchemaCreatorService;
import net.atos.digital.multitenant.tenant.hibernate.schema.SchemaSelector;
import net.atos.digital.multitenant.tenant.properties.TenantConfigurationRegistrar;
import net.atos.digital.multitenant.tenant.properties.TenantConfigurationRegistry;
import net.atos.digital.multitenant.utils.ProfileConstants;
import org.hibernate.cfg.Environment;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScanPackages;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.jdbc.DatabaseDriver;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.jdbc.support.MetaDataAccessException;
import org.springframework.orm.hibernate5.SpringBeanContainer;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.DatabaseMetaData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
@ConditionalOnClass(JpaRepository.class)
public class TenantJpaConfig {

    @Value("${spring.jpa.properties.hibernate.ddl-auto:none}") private String ddlAuto;
    @Value("${spring.jpa.properties.hibernate.show-sql:false}") private String showSql;


    @Profile(ProfileConstants.MULTI_SCHEMA_OR_MULTITENANT)
    @Bean
    @DependsOn("entityManagerFactory")
    @ConditionalOnProperty(prefix = "spring.liquibase", name = "enabled", havingValue = "false", matchIfMissing = true)
    public TableSchemaInitializer tableSchemaInitializer(TenantConfigurationRegistry tenantConfigurationRegistry,
                                                         TenantContext tenantContext, LocalContainerEntityManagerFactoryBean entityManagerFactory){
        log.info("liquibase false");
        return new TableSchemaInitializer(tenantConfigurationRegistry, tenantContext, entityManagerFactory.getObject());
    }

    @Bean
    public DatabaseSchemaInitializer databaseSchemaInitializer(SchemaCreatorService schemaCreatorService,
                                                               JdbcTemplate jdbcTemplate, TenantConfigurationRegistrar tenantConfigurationRegistrar){
        return new DatabaseSchemaInitializer(schemaCreatorService, jdbcTemplate);
    }



    @Profile(ProfileConstants.MULTI_SCHEMA_OR_MULTITENANT)
    @Bean
    public SchemaSelector schemaSelector(DataSource dataSource) throws MetaDataAccessException {
        String url = JdbcUtils.extractDatabaseMetaData(dataSource, DatabaseMetaData::getURL);
        DatabaseDriver driver = DatabaseDriver.fromJdbcUrl(url);
        SchemaSelector schemaSelector;
        if(DatabaseDriver.H2.equals(driver)){
            schemaSelector = (schema -> String.format("SET SCHEMA '%s'", schema));
        }else if(DatabaseDriver.POSTGRESQL.equals(driver)){
            schemaSelector = (schema -> String.format("SET search_path TO '%s'", schema));
        }else{
            throw new RuntimeException("Database " + driver.getDriverClassName() + " not managed");
        }
        return schemaSelector;
    }

    @Profile(ProfileConstants.MULTI_SCHEMA_OR_MULTITENANT)
    @Bean
    public SchemaBasedMultiTenantConnectionProvider multiTenantConnectionProvider(DataSource dataSource, TenantContext tenantContext, SchemaSelector schemaSelector) {

        return new SchemaBasedMultiTenantConnectionProvider(dataSource, tenantContext, schemaSelector);
    }

    @Profile(ProfileConstants.MULTI_SCHEMA_OR_MULTITENANT)
    @Bean
    public CurrentTenantIdentifierResolver currentTenantIdentifierResolver(TenantContext tenantContext) {
        return new CurrentTenantIdentifierResolverImpl(tenantContext);
    }


    @Profile(ProfileConstants.MULTI_SCHEMA_OR_MULTITENANT)
    @Primary
    @Bean
    @DependsOn("databaseSchemaInitializer")
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
        ConfigurableListableBeanFactory beanFactory,
        JpaProperties jpaProperties,
        SchemaBasedMultiTenantConnectionProvider connectionProvider,
        CurrentTenantIdentifierResolver tenantResolver) {

        LocalContainerEntityManagerFactoryBean emfBean = new LocalContainerEntityManagerFactoryBean();
        emfBean.setPersistenceUnitName("tenant-persistence-unit");

        List<String> packages = EntityScanPackages.get(beanFactory).getPackageNames();
        if (packages.isEmpty() && AutoConfigurationPackages.has(beanFactory)) {
            packages = AutoConfigurationPackages.get(beanFactory);
        }
        emfBean.setPackagesToScan(StringUtils.toStringArray(packages));

        JpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        emfBean.setJpaVendorAdapter(vendorAdapter);

        Map<String, Object> properties = new HashMap<>();

        Map<String, String> jpaProps = new HashMap<>(jpaProperties.getProperties());
        jpaProps.remove("hibernate.ddl-auto");
        jpaProps.remove("hibernate.hbm2ddl.auto");
        jpaProps.remove(Environment.HBM2DDL_AUTO);
        properties.putAll(jpaProps);

        // Naming strategies : l'application consommatrice peut les définir via
        // spring.jpa.properties.hibernate.physical_naming_strategy
        // Si non défini, on utilise le standard Hibernate (pas de conversion).
        properties.putIfAbsent(Environment.PHYSICAL_NAMING_STRATEGY, "org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl");
        properties.putIfAbsent(Environment.IMPLICIT_NAMING_STRATEGY, "org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy");
        properties.put(Environment.BEAN_CONTAINER, new SpringBeanContainer(beanFactory));        properties.remove(Environment.DEFAULT_SCHEMA);
        properties.put("hibernate.multi_tenancy", "SCHEMA");
        properties.put(Environment.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
        properties.put(Environment.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantResolver);
        properties.put(Environment.HBM2DDL_AUTO, ddlAuto);
        properties.put(Environment.SHOW_SQL, showSql);
        log.info("DDL Auto configuration applied: {} = {}", Environment.HBM2DDL_AUTO, ddlAuto);
        log.info("All Hibernate properties: {}", properties);

        emfBean.setJpaPropertyMap(properties);

        return emfBean;
    }

    @Profile(ProfileConstants.MULTI_SCHEMA_OR_MULTITENANT)
    @Primary
    @Bean
    public JpaTransactionManager transactionManager(EntityManagerFactory emf) {
        JpaTransactionManager tenantTransactionManager = new JpaTransactionManager();
        tenantTransactionManager.setEntityManagerFactory(emf);
        return tenantTransactionManager;
    }

}