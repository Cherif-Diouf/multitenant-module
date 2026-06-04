

package net.atos.digital.multitenant.tenant.properties;

import lombok.extern.slf4j.Slf4j;
import net.atos.digital.multitenant.event.NewTenantsEvent;
import net.atos.digital.multitenant.properties.SchemaConfigurationProperties;
import net.atos.digital.multitenant.utils.ProfileConstants;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConversionServiceProvider;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.bind.validation.ValidationBindHandler;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.util.*;

import static net.atos.digital.multitenant.tenant.TenantContext.DEFAULT_TENANT_UUID;

@Slf4j
@Component
public class TenantConfigurationRegistrar implements InitializingBean {
    private final ConfigManagerClient configManagerClient;
    protected ApplicationContext applicationContext;
    protected TenantConfigurationRegistry tenantConfigurationRegistry;
    private final List<Class<?>> tenantConfigurationPropertiesClasses = new ArrayList<>();
    private final boolean multitenantActive;
    private final boolean multiSchemaActive;

    private final ValidationBindHandler validationBindHandler;
    private final PropertySourcesPlaceholdersResolver placeholdersResolver;
    private final List<ConversionService> conversionServices;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    // 5 minutes
    private static final long DELAY = 5 * 1000 * 60;

    public TenantConfigurationRegistrar(ConfigManagerClient configManagerClient,
                                        ApplicationContext applicationContext,
                                        TenantConfigurationRegistry tenantConfigurationRegistry) {
        this.configManagerClient = configManagerClient;
        this.tenantConfigurationRegistry = tenantConfigurationRegistry;
        this.applicationContext = applicationContext;
        this.findTenantConfigurationPropertiesClasses();
        this.runRefreshReferential();
        multitenantActive = Arrays.stream(applicationContext.getEnvironment().getActiveProfiles())
            .anyMatch(env -> (env.equalsIgnoreCase(ProfileConstants.MULTI_TENANT)));
        multiSchemaActive = Arrays.stream(applicationContext.getEnvironment().getActiveProfiles())
            .anyMatch(env -> (env.equalsIgnoreCase(ProfileConstants.MULTI_SCHEMA)));
        this.tenantConfigurationRegistry.setMultitenantActive(multitenantActive);
        this.tenantConfigurationRegistry.setMultiSchemaActive(multiSchemaActive);
        this.validationBindHandler = new ValidationBindHandler(new TenantConfigurationPropertiesValidator(this.applicationContext));
        this.placeholdersResolver = new PropertySourcesPlaceholdersResolver(this.applicationContext.getEnvironment());
        this.conversionServices = new ConversionServiceProvider(this.applicationContext).getConversionServices();
    }

    private void findTenantConfigurationPropertiesClasses() {
        applicationContext.getBeansWithAnnotation(ComponentScan.class).forEach((name, instance) -> {
            Set<ComponentScan> componentScans = AnnotatedElementUtils.getMergedRepeatableAnnotations(ClassUtils.getUserClass(instance), ComponentScan.class);
            for (ComponentScan scan : componentScans) {
                for (String basePackage : scan.basePackages()) {
                    if (!basePackage.equals("net.atos.digital.multitenant")) {
                        Reflections reflections = new Reflections(new ConfigurationBuilder().setExpandSuperTypes(false)
                            .setUrls(ClasspathHelper.forPackage(basePackage))
                            .setScanners(new SubTypesScanner(),
                                new TypeAnnotationsScanner()));
                        this.tenantConfigurationPropertiesClasses.addAll(reflections.getTypesAnnotatedWith(TenantConfigurationProperties.class));
                    }
                }
            }
        });
    }


    @Override
    public void afterPropertiesSet() {
        log.info("Initialize TenantConfigurationRegistry");
        TenantConfigurationDTO dto = this.getData();
        if (multitenantActive) {
            log.debug("Fetching and setting TenantConfigurationRegistry");
            if (dto != null) {
                initTenantRegistry(dto);
            } else {
                log.error("No data to initialize tenant configuration properties.");
            }
            log.info("TenantConfigurationRegistry set");
        } else {
            initClassicRegistry(dto);
            log.info("Classic TenantConfigurationRegistry set");
        }
    }

    public void runRefreshReferential() {
        applicationContext.getBean(TaskScheduler.class)
            .scheduleWithFixedDelay(this::refreshRegistry, DELAY);
    }

    public void refreshRegistry() {
        if (multiSchemaActive || multitenantActive) {
            log.info("Refreshing TenantConfigurationRegistry");
            TenantConfigurationDTO dto = this.getData();
            if (dto != null) {
                List<TenantData> oldTenants = tenantConfigurationRegistry.getTenants();
                initTenantRegistry(dto);
                List<TenantData> newTenants = new ArrayList<>(tenantConfigurationRegistry.getTenants());
                newTenants.removeAll(oldTenants);
                handleNewTenants(newTenants);
                log.info("TenantConfigurationRegistry refreshed");
            } else {
                log.error("No data to refresh tenant configuration properties.");
            }
        }

    }

    private void handleNewTenants(List<TenantData> newTenants) {
        applicationEventPublisher.publishEvent(new NewTenantsEvent(this, newTenants));
    }

    protected TenantConfigurationDTO getData() {
        return configManagerClient.fetchTenantConfiguration();
    }

    private void initTenantRegistry(TenantConfigurationDTO dto) {
        if (!tenantConfigurationPropertiesClasses.isEmpty()) {
            Map<String, Map<Class<?>, Object>> configs = new HashMap<>();
            ConfigurableEnvironment env = (ConfigurableEnvironment) applicationContext.getEnvironment();
            Map<String, Object> systemProperties = ((ConfigurableEnvironment) this.applicationContext.getEnvironment()).getSystemProperties();

            dto.propertiesPerTenant().forEach((tenantName, propertyValues) -> {
                MapConfigurationPropertySource propertySource = new MapConfigurationPropertySource(propertyValues);

                CustomPlaceholdersResolver placeholdersResolver = new CustomPlaceholdersResolver(propertyValues, systemProperties, env);
                Binder binder = new Binder(Collections.singleton(propertySource), placeholdersResolver, conversionServices, null, validationBindHandler, null);
                tenantConfigurationPropertiesClasses.forEach(aClass -> {
                    TenantConfigurationProperties annotation = aClass.getAnnotation(TenantConfigurationProperties.class);
                    Object bound = binder.bindOrCreate(annotation.prefix(), aClass);
                    configs.computeIfAbsent(tenantName, k -> new HashMap<>()).put(aClass, bound);
                    configs.computeIfAbsent(DEFAULT_TENANT_UUID.toString(), k -> new HashMap<>()).put(aClass, bound);
                });
            });
            this.tenantConfigurationRegistry.setConfigs(configs);
        }
        this.tenantConfigurationRegistry.setPropertiesPerTenant(dto.propertiesPerTenant());
        this.tenantConfigurationRegistry.setTenants(dto.tenants());
    }

    private void initClassicRegistry(TenantConfigurationDTO dto) {
        if (!tenantConfigurationPropertiesClasses.isEmpty()) {
            Map<String, Map<Class<?>, Object>> configs = new HashMap<>();
            Environment environment = this.applicationContext.getEnvironment();
            Iterable<ConfigurationPropertySource> propertySources = ConfigurationPropertySources.from(((ConfigurableEnvironment) environment).getPropertySources());
            Binder binder = new Binder(propertySources, this.placeholdersResolver, conversionServices, null, validationBindHandler, null);

            tenantConfigurationPropertiesClasses.forEach(aClass -> {
                TenantConfigurationProperties annotation = aClass.getAnnotation(TenantConfigurationProperties.class);
                if (aClass.equals(SchemaConfigurationProperties.class)) {
                    return;
                }
                Object bound = binder.bindOrCreate(annotation.prefix(), aClass);
                configs.computeIfAbsent(DEFAULT_TENANT_UUID.toString(), k -> new HashMap<>()).put(aClass, bound);
            });

            this.tenantConfigurationRegistry.setConfigs(configs);
        }
        this.tenantConfigurationRegistry.setTenants(dto.tenants());
    }
}