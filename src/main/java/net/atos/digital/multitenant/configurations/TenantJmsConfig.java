

package net.atos.digital.multitenant.configurations;

import jakarta.jms.ConnectionFactory;
import net.atos.digital.multitenant.tenant.jms.MultitenantSimpleMessageConverter;
import net.atos.digital.multitenant.tenant.jms.TenantMappingJackson2MessageConverter;
import net.atos.digital.multitenant.utils.ProfileConstants;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.jms.DefaultJmsListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.destination.DynamicDestinationResolver;


@Profile(ProfileConstants.MULTI_SCHEMA_OR_MULTITENANT)
@Configuration
@ConditionalOnClass(JmsTemplate.class)
public class TenantJmsConfig {

    @Primary
    @Bean
    public JmsTemplate queueJmsTemplate(ConnectionFactory connectionFactory, TenantMappingJackson2MessageConverter messageConverter) {
        JmsTemplate jmsTemplate = new JmsTemplate(connectionFactory);
        jmsTemplate.setDestinationResolver(new DynamicDestinationResolver());
        jmsTemplate.setPriority(4);
        jmsTemplate.setTimeToLive(30000L);
        jmsTemplate.setExplicitQosEnabled(true);
        jmsTemplate.setMessageConverter(messageConverter);
        return jmsTemplate;
    }

    @Primary
    @Bean
    public JmsTemplate topicJmsTemplate(ConnectionFactory connectionFactory, TenantMappingJackson2MessageConverter messageConverter) {
        JmsTemplate jmsTemplate = new JmsTemplate(connectionFactory);
        jmsTemplate.setPubSubDomain(true);
        jmsTemplate.setDestinationResolver(new DynamicDestinationResolver());
        jmsTemplate.setMessageConverter(messageConverter);
        return jmsTemplate;
    }

    @Primary
    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(DefaultJmsListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory, MultitenantSimpleMessageConverter messageConverter) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(messageConverter);
        return factory;
    }
}
