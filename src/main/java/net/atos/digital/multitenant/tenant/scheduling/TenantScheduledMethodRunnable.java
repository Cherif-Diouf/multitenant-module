

package net.atos.digital.multitenant.tenant.scheduling;

import lombok.Getter;
import net.atos.digital.multitenant.tenant.Tenant;
import net.atos.digital.multitenant.tenant.TenantContext;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;

/**
 * Runnable for scheduled tasks
 * Use the tenant to set context when running
 *
 * @see TenantScheduledAnnotationBeanPostProcessor
 */
public class TenantScheduledMethodRunnable extends ScheduledMethodRunnable {
    private final TenantContext tenantContext;
    @Getter
    private final Tenant tenant;

    public TenantScheduledMethodRunnable(Object target, Method method, final TenantContext tenantContext) {
        super(target, method);
        this.tenantContext = tenantContext;
        this.tenant = tenantContext.getCurrentTenant();
    }

    @Override
    public void run() {
        try {
            this.tenantContext.setCurrentTenant(tenant);
            ReflectionUtils.makeAccessible(this.getMethod());
            this.getMethod().invoke(this.getTarget());
        }
        catch (InvocationTargetException ex) {
            ReflectionUtils.rethrowRuntimeException(ex.getTargetException());
        }
        catch (IllegalAccessException ex) {
            throw new UndeclaredThrowableException(ex);
        }finally {
            this.tenantContext.clear();
        }
    }
}
