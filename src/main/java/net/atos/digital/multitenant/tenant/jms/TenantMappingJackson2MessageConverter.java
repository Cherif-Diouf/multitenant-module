

package net.atos.digital.multitenant.tenant.jms;


import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Session;
import net.atos.digital.multitenant.tenant.TenantContext;
import net.atos.digital.multitenant.tenant.properties.TenantConfigurationRegistry;
import net.atos.digital.multitenant.utils.ProfileConstants;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConversionException;
import org.springframework.jms.support.converter.MessageType;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnClass(JmsTemplate.class)
public class TenantMappingJackson2MessageConverter extends MappingJackson2MessageConverter {
    private final TenantContext tenantContext;
    private final TenantConfigurationRegistry tenantConfigurationRegistry;


    public TenantMappingJackson2MessageConverter(TenantContext tenantContext, TenantConfigurationRegistry tenantConfigurationRegistry) {
        super();
        this.tenantContext = tenantContext;
        this.tenantConfigurationRegistry = tenantConfigurationRegistry;
        this.setTargetType(MessageType.TEXT);
        this.setTypeIdPropertyName("_type");
    }


    @Override
    public Message toMessage(Object object, Session session) throws JMSException, MessageConversionException {
        if (tenantContext.getCurrentTenant() == null) {
            throw new MessageConversionException("No current tenant in context; unable to send JMS message.");
        }
        Message message = super.toMessage(object, session);

        message.setStringProperty(ProfileConstants.TENANT, tenantContext.getCurrentTenantName());

        return message;
    }


    @Override
    public Object fromMessage(Message message) throws JMSException, MessageConversionException {
        String tenant = message.getStringProperty(ProfileConstants.TENANT);
        tenantContext.setCurrentTenant(tenantConfigurationRegistry.findTenantByName(tenant).toTenant());

        return super.fromMessage(message);
    }
}