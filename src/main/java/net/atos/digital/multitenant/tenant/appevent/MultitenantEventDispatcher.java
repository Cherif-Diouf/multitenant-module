package net.atos.digital.multitenant.tenant.appevent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.atos.digital.multitenant.tenant.Tenant;
import net.atos.digital.multitenant.tenant.TenantContext;
import net.atos.digital.multitenant.utils.ProfileConstants;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@Profile(ProfileConstants.MULTI_SCHEMA_OR_MULTITENANT)
public class MultitenantEventDispatcher implements ApplicationListener<MultitenantEventWrapper<?>> {

    private final TenantContext tenantContext;
    private final ApplicationEventPublisher publisher;

    @Override
    public void onApplicationEvent(MultitenantEventWrapper<?> wrapper) {
        Tenant previousTenant = tenantContext.getCurrentTenant();
        try {
            tenantContext.setCurrentTenant(wrapper.getTenant());
            publisher.publishEvent(wrapper.getOriginalEvent());
        } finally {
            if (previousTenant != null) tenantContext.setCurrentTenant(previousTenant);
            else tenantContext.clear();
        }
    }
}
