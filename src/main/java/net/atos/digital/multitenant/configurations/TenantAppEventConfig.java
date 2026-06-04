package net.atos.digital.multitenant.configurations;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.atos.digital.messagebroker.appevent.service.EventNotifier;
import net.atos.digital.multitenant.tenant.TenantContext;
import net.atos.digital.multitenant.tenant.appevent.MultiTenantEventPublisherProxy;
import net.atos.digital.multitenant.tenant.appevent.MultitenantEventNotifier;
import net.atos.digital.multitenant.utils.ProfileConstants;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.ExecutorService;

@Slf4j
@Configuration
@Profile(ProfileConstants.MULTI_SCHEMA_OR_MULTITENANT)
@EnableAspectJAutoProxy(
        proxyTargetClass = true,
        exposeProxy = true
)
public class TenantAppEventConfig {

    private MultiTenantEventPublisherProxy proxyHandler;

    @Bean("multitenantApplicationEventPublisher")
    @Primary
    public ApplicationEventPublisher multitenantApplicationEventPublisher(
            ApplicationContext applicationContext,
            TenantContext tenantContext,
            @Value("${multitenant.events.allowed-packages:}") List<String> allowedPackages) {

        try {
            if (allowedPackages == null || allowedPackages.isEmpty()) {
                log.warn("No allowed packages configured for multitenant events. " +
                        "All events will be published directly without tenant context.");
                allowedPackages = List.of();
            }

            log.info("Creating multitenant ApplicationEventPublisher proxy");
            log.info("Allowed packages ({}): {}", allowedPackages.size(), allowedPackages);

            this.proxyHandler = new MultiTenantEventPublisherProxy(
                    applicationContext,
                    tenantContext, allowedPackages);

            ApplicationEventPublisher proxy = (ApplicationEventPublisher) Proxy.newProxyInstance(
                    ApplicationEventPublisher.class.getClassLoader(),
                    new Class<?>[]{ApplicationEventPublisher.class},
                    proxyHandler
            );

            log.info("Multitenant ApplicationEventPublisher proxy created successfully");
            return proxy;

        } catch (Exception e) {
            log.error("Failed to create multitenant ApplicationEventPublisher proxy: {}",
                    e.getMessage(), e);

            log.warn("Falling back to default ApplicationEventPublisher");
            return applicationContext;
        }
    }

    @Bean
    @Primary
    @DependsOn("multitenantApplicationEventPublisher")
    //@ConditionalOnClass(EventNotifier.class)
    @ConditionalOnBean(name = "appEventThreadConfig")
    public EventNotifier eventNotifier( @Qualifier("multitenantApplicationEventPublisher") ApplicationEventPublisher applicationEventPublisher, @Qualifier("appEventThreadConfig") ExecutorService executor, TenantContext tenantContext) {
        return new MultitenantEventNotifier(executor, tenantContext, applicationEventPublisher);
    }

    @PreDestroy
    public void cleanup() {
        if (proxyHandler != null && log.isInfoEnabled()) {

        }
    }
}