package net.atos.digital.multitenant.filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import net.atos.digital.multitenant.TenantException;
import net.atos.digital.multitenant.properties.SchemaConfigurationProperties;
import net.atos.digital.multitenant.tenant.Tenant;
import net.atos.digital.multitenant.tenant.TenantContext;
import net.atos.digital.multitenant.tenant.properties.TenantConfigurationRegistry;
import net.atos.digital.multitenant.tenant.properties.TenantData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
@DisplayName("TenantFilter")
class TenantFilterTest {
    @Mock private TenantContext tenantContext;
    @Mock private TenantConfigurationRegistry registry;
    @Mock private FilterChain chain;
    private SchemaConfigurationProperties schemaProps;
    private TenantFilter filter;
    private static final String TENANT_NAME = "acme";
    private static final UUID TENANT_UUID = UUID.randomUUID();
    @BeforeEach
    void setUp() {
        schemaProps = new SchemaConfigurationProperties();
        filter = new TenantFilter(tenantContext, "/actuator", schemaProps, registry);
    }
    @Test
    @DisplayName("multitenant: X-TENANT valide => contexte alimente, chaine continuee")
    void multitenant_validHeader_setsTenantAndContinues() throws Exception {
        when(registry.isMultitenantActive()).thenReturn(true);
        TenantData data = new TenantData(TENANT_UUID, TENANT_NAME, "ACME Corp", true);
        when(registry.findTenantByName(TENANT_NAME)).thenReturn(data);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/resource");
        req.addHeader("X-TENANT", TENANT_NAME);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilterInternal(req, res, chain);
        ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantContext).setCurrentTenant(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo(TENANT_NAME);
        assertThat(captor.getValue().schemaName()).isEqualTo(TENANT_NAME);
        verify(chain).doFilter(req, res);
        verify(tenantContext).clear();
        assertThat(res.getStatus()).isEqualTo(200);
    }
    @Test
    @DisplayName("multitenant: X-TENANT absent => 400")
    void multitenant_missingHeader_returns400() throws Exception {
        when(registry.isMultitenantActive()).thenReturn(true);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/resource");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilterInternal(req, res, chain);
        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verify(chain, never()).doFilter(any(), any());
    }
    @Test
    @DisplayName("multitenant: tenant inconnu => 400")
    void multitenant_unknownTenant_returns400() throws Exception {
        when(registry.isMultitenantActive()).thenReturn(true);
        when(registry.findTenantByName("unknown")).thenThrow(new TenantException("not found"));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/resource");
        req.addHeader("X-TENANT", "unknown");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilterInternal(req, res, chain);
        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verify(chain, never()).doFilter(any(), any());
    }
    @Test
    @DisplayName("multiSchema: X-TENANT absent => schema par defaut utilise")
    void multiSchema_noHeader_usesDefaultSchema() throws Exception {
        when(registry.isMultitenantActive()).thenReturn(false);
        schemaProps.setDefaultSchema(new SchemaConfigurationProperties.ItemSchemaConfiguration(List.of(), true, "public", List.of()));
        when(registry.findTenantByName("public")).thenThrow(new TenantException("not found"));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/resource");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilterInternal(req, res, chain);
        ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantContext).setCurrentTenant(captor.capture());
        assertThat(captor.getValue().schemaName()).isEqualTo("public");
        verify(chain).doFilter(req, res);
    }
    @Test
    @DisplayName("multiSchema: schema inconnu => 400")
    void multiSchema_unknownSchema_returns400() throws Exception {
        when(registry.isMultitenantActive()).thenReturn(false);
        schemaProps.setDefaultSchema(new SchemaConfigurationProperties.ItemSchemaConfiguration(List.of(), true, "public", List.of()));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/resource");
        req.addHeader("X-TENANT", "unknown_schema");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilterInternal(req, res, chain);
        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verify(chain, never()).doFilter(any(), any());
    }
    @Test
    @DisplayName("shouldNotFilter: /actuator/** => vrai")
    void actuatorUrl_shouldNotFilter() throws Exception {
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/actuator/health"))).isTrue();
    }
    @Test
    @DisplayName("shouldNotFilter: /swagger-ui/** => vrai")
    void swaggerUrl_shouldNotFilter() throws Exception {
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/swagger-ui/index.html"))).isTrue();
    }
    @Test
    @DisplayName("shouldNotFilter: /api/resource => faux")
    void apiResource_shouldFilter() throws Exception {
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/resource"))).isFalse();
    }
}