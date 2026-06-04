package net.atos.digital.multitenant.tenant.kafka;

import net.atos.digital.multitenant.tenant.Tenant;
import net.atos.digital.multitenant.tenant.TenantContext;
import net.atos.digital.multitenant.tenant.properties.TenantConfigurationRegistry;
import net.atos.digital.multitenant.tenant.properties.TenantData;
import net.atos.digital.multitenant.utils.ProfileConstants;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.converter.ConversionException;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MultitenantKafkaMessageConverterTest {

    private static final String TENANT_NAME = "acme";
    private static final UUID TENANT_UUID = UUID.randomUUID();

    @Mock
    private TenantContext tenantContext;

    @Mock
    private TenantConfigurationRegistry registry;

    private MultitenantKafkaMessageConverter converter;

    @BeforeEach
    void setUp() {
        converter = new MultitenantKafkaMessageConverter(tenantContext, registry);
    }

    @Test
    void initialRecordHeadersInjectsTenantHeader() {
        Tenant tenant = new Tenant(TENANT_UUID, TENANT_NAME, TENANT_NAME);
        when(tenantContext.getCurrentTenant()).thenReturn(tenant);
        when(tenantContext.getCurrentTenantName()).thenReturn(TENANT_NAME);
        Message<String> message = MessageBuilder.withPayload("payload").build();

        Headers headers = converter.initialRecordHeaders(message);

        String tenantHeader = new String(headers.lastHeader(ProfileConstants.TENANT).value(), StandardCharsets.UTF_8);
        assertThat(tenantHeader).isEqualTo(TENANT_NAME);
    }

    @Test
    void initialRecordHeadersWithoutTenantThrowsConversionException() {
        when(tenantContext.getCurrentTenant()).thenReturn(null);
        Message<String> message = MessageBuilder.withPayload("payload").build();

        assertThatThrownBy(() -> converter.initialRecordHeaders(message))
                .isInstanceOf(ConversionException.class);
    }

    @Test
    void extractAndConvertValueSetsTenantContextFromHeader() {
        TenantData data = new TenantData(TENANT_UUID, TENANT_NAME, "ACME", true);
        when(registry.findTenantByName(TENANT_NAME)).thenReturn(data);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0L, "key", "\"payload\"");
        record.headers().add(ProfileConstants.TENANT, TENANT_NAME.getBytes(StandardCharsets.UTF_8));

        converter.extractAndConvertValue(record, String.class);

        ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantContext).setCurrentTenant(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo(TENANT_NAME);
    }

    @Test
    void extractAndConvertValueWithoutTenantHeaderDoesNotSetContext() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic", 0, 0L, "key", "\"payload\"");

        converter.extractAndConvertValue(record, String.class);

        verify(tenantContext, never()).setCurrentTenant(any());
    }
}