package net.atos.digital.multitenant.tenant.jms;

import jakarta.jms.Message;
import net.atos.digital.multitenant.tenant.Tenant;
import net.atos.digital.multitenant.tenant.TenantContext;
import net.atos.digital.multitenant.tenant.properties.TenantConfigurationRegistry;
import net.atos.digital.multitenant.tenant.properties.TenantData;
import net.atos.digital.multitenant.utils.ProfileConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jms.support.converter.MessageConversionException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantMappingJackson2MessageConverterTest {

    private static final String TENANT_NAME = "acme";
    private static final UUID TENANT_UUID = UUID.randomUUID();

    @Mock
    private TenantContext tenantContext;

    @Mock
    private TenantConfigurationRegistry registry;

    @Mock
    private Message jmsMessage;

    private TenantMappingJackson2MessageConverter converter;

    @BeforeEach
    void setUp() {
        converter = new TenantMappingJackson2MessageConverter(tenantContext, registry);
    }

    @Test
    void fromMessageSetsTenantContextFromHeader() throws Exception {
        TenantData data = new TenantData(TENANT_UUID, TENANT_NAME, "ACME", true);
        when(registry.findTenantByName(TENANT_NAME)).thenReturn(data);
        when(jmsMessage.getStringProperty(ProfileConstants.TENANT)).thenReturn(TENANT_NAME);

        try {
            converter.fromMessage(jmsMessage);
        } catch (Exception ignored) {
            // super.fromMessage may fail on mocked JMS internals; tenant propagation is still verified.
        }

        ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantContext).setCurrentTenant(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo(TENANT_NAME);
    }

    @Test
    void toMessageWithoutTenantThrowsException() {
        when(tenantContext.getCurrentTenant()).thenReturn(null);

        assertThatThrownBy(() -> converter.toMessage("payload", null))
                .isInstanceOf(MessageConversionException.class);
    }
}