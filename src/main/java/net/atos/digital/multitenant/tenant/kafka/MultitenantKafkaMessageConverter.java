package net.atos.digital.multitenant.tenant.kafka;

import net.atos.digital.multitenant.tenant.TenantContext;
import net.atos.digital.multitenant.tenant.properties.TenantConfigurationRegistry;
import net.atos.digital.multitenant.utils.ProfileConstants;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.converter.ConversionException;
import org.springframework.kafka.support.converter.JsonMessageConverter;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnClass(KafkaTemplate.class)
public class MultitenantKafkaMessageConverter extends JsonMessageConverter {

    private final TenantContext tenantContext;
    private final TenantConfigurationRegistry tenantConfigurationRegistry;

    public MultitenantKafkaMessageConverter(TenantContext tenantContext,
                                            TenantConfigurationRegistry tenantConfigurationRegistry) {
        super();
        this.tenantContext = tenantContext;
        this.tenantConfigurationRegistry = tenantConfigurationRegistry;
    }

    @Override
    protected Headers initialRecordHeaders(Message<?> message) {
        if (tenantContext.getCurrentTenant() == null) {
            throw new ConversionException("No current tenant in context; unable to send Kafka message.", message, null);
        }

        Headers headers = super.initialRecordHeaders(message);

        RecordHeaders recordHeaders = new RecordHeaders(headers);

        recordHeaders.add(ProfileConstants.TENANT, tenantContext.getCurrentTenantName().getBytes(StandardCharsets.UTF_8));

        return recordHeaders;
    }

    @Override
    protected Object extractAndConvertValue(ConsumerRecord<?, ?> record, Type type) {
        Headers headers = record.headers();
        String tenant = getHeaderValue(headers, ProfileConstants.TENANT);

        if (tenant != null) {
            try {
                tenantContext.setCurrentTenant(tenantConfigurationRegistry.findTenantByName(tenant)
                        .toTenant());
            } catch (Exception e) {
                throw new ConversionException("Error configuring tenant: " + tenant, record, e);
            }
        }

        return super.extractAndConvertValue(record, type);
    }

    private String getHeaderValue(Headers headers, String key) {
        Header header = headers.lastHeader(key);
        if (header != null && header.value() != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        return null;
    }
}