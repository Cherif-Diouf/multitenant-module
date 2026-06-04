

package net.atos.digital.multitenant.tenant.scheduling;

import lombok.extern.slf4j.Slf4j;
import net.atos.digital.multitenant.tenant.Tenant;
import net.atos.digital.multitenant.tenant.TenantContext;
import net.atos.digital.multitenant.tenant.TenantService;
import net.atos.digital.multitenant.tenant.properties.TenantConfigurationRegistry;
import net.atos.digital.multitenant.tenant.properties.TenantData;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.util.Assert;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Bean post-processor that registers methods annotated with @{@link TenantScheduled}
 * to be invoked by a {@link TaskScheduler} according
 * to the "fixedRate", "fixedDelay", or "cron" expression provided via the annotation.
 *
 * It creates tasks depending on whether multitenant is active.
 * If active, a @{@link TenantScheduledMethodRunnable} is created per tenant
 * else the normal @{link ScheduledMethodRunnable} is used
 *
 * @see TenantScheduled
 * @see EnableScheduling
 * @see TenantScheduledMethodRunnable
 */
@Slf4j
public class TenantScheduledAnnotationBeanPostProcessor extends ScheduledAnnotationBeanPostProcessor {
    private final Set<Class<?>> nonAnnotatedClasses = Collections.newSetFromMap(new ConcurrentHashMap<>(64));
    private TenantConfigurationRegistry tenantConfigurationRegistry;
    private final TenantService tenantService;
    private TenantContext tenantContext;
    private Map<Object, Map<Method, Set<TenantScheduled>>> beanAnnotatedMethods = new HashMap<>();

    public TenantScheduledAnnotationBeanPostProcessor(TenantConfigurationRegistry tenantConfigurationRegistry, TenantService tenantService, TenantContext tenantContext) {
        super();
        this.tenantConfigurationRegistry = tenantConfigurationRegistry;
        this.tenantService = tenantService;
        this.tenantContext = tenantContext;
    }

    private Map<Method, Set<TenantScheduled>> getAnnotatedMethods(Class<?> targetClass) {
        Map<Method, Set<TenantScheduled>> annotatedMethods = MethodIntrospector.selectMethods(targetClass,
            (MethodIntrospector.MetadataLookup<Set<TenantScheduled>>) method -> {
                Set<TenantScheduled> tenantScheduleds = new HashSet<>();
                TenantScheduled annotation = AnnotationUtils.getAnnotation(method, TenantScheduled.class);
                if(annotation != null){
                    tenantScheduleds.add(annotation);
                    return tenantScheduleds;
                }
                return null;
            });
        return annotatedMethods;
    }


    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        log.debug("Processing @TenantScheduled methods");
        if (bean instanceof AopInfrastructureBean || bean instanceof TaskScheduler ||
            bean instanceof ScheduledExecutorService) {
            // Ignore AOP infrastructure such as scoped proxies.
            return bean;
        }

        Class<?> targetClass = AopProxyUtils.ultimateTargetClass(bean);
        if (!this.nonAnnotatedClasses.contains(targetClass) &&
            AnnotationUtils.isCandidateClass(targetClass, Arrays.asList(TenantScheduled.class))) {
            Map<Method, Set<TenantScheduled>> annotatedMethods = getAnnotatedMethods(targetClass);
            if (annotatedMethods.isEmpty()) {
                this.nonAnnotatedClasses.add(targetClass);
                if (logger.isTraceEnabled()) {
                    logger.trace("No @TenantScheduled annotations found on bean class: " + targetClass);
                }
            }
            else {
                // Non-empty set of methods
                this.beanAnnotatedMethods.put(bean, annotatedMethods);
                annotatedMethods.forEach((method, scheduledMethods) ->
                    scheduledMethods.forEach(tenantScheduled -> {
                        if(tenantConfigurationRegistry.isMultiSchemaActive()) {
                            tenantService.getSchedulableTenants().forEach(t -> {
                                tenantContext.setCurrentTenant(t);
                                processScheduled(tenantScheduled.scheduled(), method, bean);
                                tenantContext.clear();
                            });
                        } else{
                            processScheduled(tenantScheduled.scheduled(), method, bean);
                        }
                    }));
                if (logger.isTraceEnabled()) {
                    logger.trace(annotatedMethods.size() + " @TenantScheduled methods processed on bean '" + beanName +
                        "': " + annotatedMethods);
                }
            }
        }
        return bean;
    }

    public void multitenantProcessScheduled(List<TenantData> tenants) {
        this.beanAnnotatedMethods.forEach((bean, annotatedMethods) ->
            annotatedMethods.forEach((method, scheduledMethods) ->
                scheduledMethods.forEach(tenantScheduled -> {
                    tenants.forEach(t -> {
                        tenantContext.setCurrentTenant(t.toTenant());
                        processScheduled(tenantScheduled.scheduled(), method, bean);
                        tenantContext.clear();
                    });
                })));
    }

    @Override
    protected Runnable createRunnable(Object target, Method method) {
        Assert.isTrue(method.getParameterCount() == 0, "Only no-arg methods may be annotated with @Scheduled");
        Method invocableMethod = AopUtils.selectInvocableMethod(method, target.getClass());
        if(tenantConfigurationRegistry.isMultitenantActive()) {
            return new TenantScheduledMethodRunnable(target, invocableMethod, tenantContext);
        }else{
            return new ScheduledMethodRunnable(target, invocableMethod);
        }
    }
}