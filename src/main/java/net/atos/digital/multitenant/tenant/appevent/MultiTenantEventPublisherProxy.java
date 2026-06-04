package net.atos.digital.multitenant.tenant.appevent;

import lombok.extern.slf4j.Slf4j;
import net.atos.digital.multitenant.tenant.Tenant;
import net.atos.digital.multitenant.tenant.TenantContext;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.List;

@Slf4j
public class MultiTenantEventPublisherProxy implements InvocationHandler {

    private final ApplicationEventPublisher delegate;
    private final TenantContext tenantContext;
    private final List<String> allowedPackages;

    private static final ThreadLocal<Boolean> dispatching = ThreadLocal.withInitial(() -> false);

    public MultiTenantEventPublisherProxy(ApplicationEventPublisher delegate, TenantContext tenantContext, List<String> allowedPackages) {
        this.delegate = delegate;
        this.tenantContext = tenantContext;
        this.allowedPackages = allowedPackages;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if ("publishEvent".equals(method.getName()) && args != null && args.length == 1) {
            return handlePublishEvent(args[0]);
        }
        return method.invoke(delegate, args);
    }

    private Object handlePublishEvent(Object event) {
        if (event == null) {
            log.warn("Attempted to publish null event");
            return null;
        }
        if (dispatching.get()) {
            delegate.publishEvent(event);
            return null;
        }
        String eventPackage = (event.getClass().getPackage() != null)
                ? event.getClass().getPackage().getName()
                : "";
        if (!isAllowedPackage(eventPackage)) {
            log.debug("Event {} from package {} not in allowed packages, delegating to default publisher",
                    event.getClass().getSimpleName(), eventPackage);
            delegate.publishEvent(event);
            return null;
        }

        try {
            dispatching.set(true);
            if (event instanceof MultitenantEventWrapper) {
                delegate.publishEvent(event);
                return null;
            }
            Tenant tenant = tenantContext.getCurrentTenant();
            if (tenant == null) {
                delegate.publishEvent(event);
                return null;
            }
            MultitenantEventWrapper<Object> wrappedEvent = new MultitenantEventWrapper<>(tenant, event);
            delegate.publishEvent(wrappedEvent);
        } finally {
            dispatching.set(false);
        }
        return null;
    }

    private boolean isAllowedPackage(String packageName) {
        return allowedPackages.stream()
                .anyMatch(packageName::startsWith);
    }
}
