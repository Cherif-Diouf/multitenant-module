package net.atos.digital.multitenant.configurations;

import lombok.extern.slf4j.Slf4j;
import net.atos.digital.multitenant.tenant.Tenant;
import net.atos.digital.multitenant.tenant.TenantContext;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration automatique de la propagation du contexte tenant dans les tâches {@code @Async}.
 * <p>
 * Implémente {@link AsyncConfigurer} pour que Spring utilise un executor qui capture et
 * restaure le {@link TenantContext} (basé sur {@code ThreadLocal}) dans les threads du pool.
 * <p>
 * Cela couvre :
 * <ul>
 *   <li>Les méthodes annotées {@code @Async}</li>
 *   <li>Spring Modulith {@code @ApplicationModuleListener}</li>
 *   <li>Tout appel transitant par le framework async de Spring</li>
 * </ul>
 * <p>
 * L'application consommatrice peut surcharger ce comportement en déclarant son propre bean
 * {@link AsyncConfigurer} — dans ce cas cette configuration est ignorée (grâce à
 * {@code @ConditionalOnMissingBean}).
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnMissingBean(AsyncConfigurer.class)
public class TenantAsyncConfig implements AsyncConfigurer {

    private final TenantContext tenantContext;

    public TenantAsyncConfig(TenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("tenant-async-");
        executor.setTaskDecorator(tenantPropagatingDecorator());
        executor.initialize();
        log.info("TenantAsyncConfig: ThreadPoolTaskExecutor créé avec propagation du TenantContext");
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler();
    }

    /**
     * {@link TaskDecorator} qui capture le tenant courant dans le thread appelant
     * et le restaure dans le thread d'exécution du pool.
     */
    private TaskDecorator tenantPropagatingDecorator() {
        return runnable -> {
            Tenant captured = tenantContext.getCurrentTenant();
            return () -> {
                try {
                    if (captured != null) {
                        tenantContext.setCurrentTenant(captured);
                    }
                    runnable.run();
                } finally {
                    tenantContext.clear();
                }
            };
        };
    }
}
