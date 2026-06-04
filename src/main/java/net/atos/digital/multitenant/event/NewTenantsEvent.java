

package net.atos.digital.multitenant.event;

import net.atos.digital.multitenant.tenant.properties.TenantData;
import org.springframework.context.ApplicationEvent;

import java.util.List;

public class NewTenantsEvent extends ApplicationEvent {

    private List<TenantData> tenants;
    /**
     * Create a new {@code ApplicationEvent}.
     *
     * @param source the object on which the event initially occurred or with
     *               which the event is associated (never {@code null})
     * @param tenants
     */
    public NewTenantsEvent(Object source, List<TenantData> tenants) {
        super(source);
        this.tenants = tenants;
    }

    public List<TenantData> getTenants() {
        return tenants;
    }
}
