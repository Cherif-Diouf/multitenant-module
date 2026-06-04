

package net.atos.digital.multitenant.tenant.cache;

import net.atos.digital.multitenant.tenant.TenantContext;
import net.atos.digital.multitenant.tenant.properties.TenantConfigurationRegistry;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.SimpleKey;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MultitenantKeyGenerator implements KeyGenerator {

    private final TenantConfigurationRegistry tenantConfigurationRegistry;

    private final TenantContext tenantContext;

    public MultitenantKeyGenerator(TenantContext tenantContext, TenantConfigurationRegistry tenantConfigurationRegistry) {
        this.tenantContext = tenantContext;
        this.tenantConfigurationRegistry = tenantConfigurationRegistry;
    }

    @Override
    public Object generate(Object target, Method method, Object... params) {
        List<Object> keyElements = new ArrayList<>(params.length + 3);
        keyElements.add(target.getClass().getSimpleName());
        keyElements.add(method.getName());

        if (tenantConfigurationRegistry.isMultiSchemaActive()) {
            keyElements.add(tenantContext.getCurrentSchemaName());
        }

        keyElements.addAll(Arrays.asList(params));

        return new SimpleKey(keyElements.toArray());
    }
}
