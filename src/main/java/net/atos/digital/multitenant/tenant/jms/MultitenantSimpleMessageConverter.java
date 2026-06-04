

package net.atos.digital.multitenant.tenant.jms;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import net.atos.digital.multitenant.tenant.TenantContext;
import net.atos.digital.multitenant.tenant.properties.TenantConfigurationRegistry;
import net.atos.digital.multitenant.utils.ProfileConstants;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MessageConversionException;
import org.springframework.jms.support.converter.SimpleMessageConverter;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnClass(JmsTemplate.class)
public class MultitenantSimpleMessageConverter extends SimpleMessageConverter {

    private final TenantContext tenantContext;
    private final TenantConfigurationRegistry tenantConfigurationRegistry;

    public MultitenantSimpleMessageConverter(TenantContext tenantContext, TenantConfigurationRegistry tenantConfigurationRegistry) {
        super();
        this.tenantContext = tenantContext;
        this.tenantConfigurationRegistry = tenantConfigurationRegistry;
    }

    @Override
    public Object fromMessage(Message message) throws JMSException, MessageConversionException {
        Object o = super.fromMessage(message);
        String tenant = message.getStringProperty(ProfileConstants.TENANT);

        tenantContext.setCurrentTenant(tenantConfigurationRegistry.findTenantByName(tenant).toTenant());
        return o;
    }
}