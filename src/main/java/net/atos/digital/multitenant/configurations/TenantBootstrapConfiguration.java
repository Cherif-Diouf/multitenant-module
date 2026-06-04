

package net.atos.digital.multitenant.configurations;

import net.atos.digital.multitenant.tenant.TenantContext;
import net.atos.digital.multitenant.tenant.properties.TenantConfigurationRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.ClassUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties
public class TenantBootstrapConfiguration {

    @Bean
    public TenantContext tenantContext(){
        return new TenantContext();
    }

    @Bean
    public TenantConfigurationRegistry tenantConfigurationRegistry(TenantContext tenantContext, Environment environment){
        return new TenantConfigurationRegistry(tenantContext, environment);
    }

    @Bean
    public TaskScheduler taskScheduler() {
        return new ThreadPoolTaskScheduler();
    }

    @Bean
    public Set<String> applicationBasePackages(ApplicationContext applicationContext){
        Set<String> applicationBasePackages = new HashSet<>();
        applicationContext.getBeansWithAnnotation(ComponentScan.class).forEach((name, instance) -> {
            Set<ComponentScan> componentScans = AnnotatedElementUtils.getMergedRepeatableAnnotations(ClassUtils.getUserClass(instance), ComponentScan.class);
            for (ComponentScan scan : componentScans) {
                applicationBasePackages.addAll(Arrays.asList(scan.basePackages()));
            }
        });
        return applicationBasePackages;
    }
}
