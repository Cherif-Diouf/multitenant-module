package net.atos.digital.multitenant.tenant.properties;

import net.atos.digital.multitenant.properties.ConfigManagerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ConfigManagerClientTest {

    @Test
    void shouldReturnRegistryWhenApplicationExists() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ConfigManagerProperties properties = defaultProperties();
        server.expect(once(), requestTo("http://localhost:8090/api/config/v1/registry/atos-sign"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"tenants\":[],\"propertiesPerTenant\":{}}", MediaType.APPLICATION_JSON));

        ConfigManagerClient client = new ConfigManagerClient(properties, "atos-sign", "", restTemplate);

        TenantConfigurationDTO dto = client.fetchTenantConfiguration();

        assertThat(dto.tenants()).isEmpty();
        assertThat(dto.propertiesPerTenant()).isEmpty();
        server.verify();
    }

    @Test
    void shouldCreateApplicationWhenRegistryNotFound() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ConfigManagerProperties properties = defaultProperties();

        server.expect(once(), requestTo("http://localhost:8090/api/config/v1/registry/atos-sign"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));
        server.expect(once(), requestTo("http://localhost:8090/api/config/v1/application"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withNoContent());
        server.expect(once(), requestTo("http://localhost:8090/api/config/v1/registry/atos-sign"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"tenants\":[],\"propertiesPerTenant\":{}}", MediaType.APPLICATION_JSON));

        ConfigManagerClient client = new ConfigManagerClient(properties, "atos-sign", "", restTemplate);

        TenantConfigurationDTO dto = client.fetchTenantConfiguration();

        assertThat(dto.tenants()).isEmpty();
        server.verify();
    }

    @Test
    void shouldThrowWhenFailFastEnabledAndConfigManagerDown() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ConfigManagerProperties properties = defaultProperties();
        properties.setFailFast(true);

        server.expect(once(), requestTo("http://localhost:8090/api/config/v1/registry/atos-sign"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR));

        ConfigManagerClient client = new ConfigManagerClient(properties, "atos-sign", "", restTemplate);

        assertThatThrownBy(client::fetchTenantConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unable to initialize tenant configuration from config-manager");
        server.verify();
    }

    @Test
    void shouldReturnEmptyWhenAutoCreateDisabledAndRegistryNotFound() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ConfigManagerProperties properties = defaultProperties();
        properties.setAutoCreateApplication(false);

        server.expect(once(), requestTo("http://localhost:8090/api/config/v1/registry/atos-sign"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        ConfigManagerClient client = new ConfigManagerClient(properties, "atos-sign", "", restTemplate);

        TenantConfigurationDTO dto = client.fetchTenantConfiguration();

        assertThat(dto.tenants()).isEmpty();
        assertThat(dto.propertiesPerTenant()).isEmpty();
        server.verify();
    }

    @Test
    void shouldContinueWhenCreateReturnsConflict() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ConfigManagerProperties properties = defaultProperties();

        server.expect(once(), requestTo("http://localhost:8090/api/config/v1/registry/atos-sign"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(once(), requestTo("http://localhost:8090/api/config/v1/application"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT));
        server.expect(once(), requestTo("http://localhost:8090/api/config/v1/registry/atos-sign"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"tenants\":[],\"propertiesPerTenant\":{}}", MediaType.APPLICATION_JSON));

        ConfigManagerClient client = new ConfigManagerClient(properties, "atos-sign", "", restTemplate);

        TenantConfigurationDTO dto = client.fetchTenantConfiguration();

        assertThat(dto.tenants()).isEmpty();
        server.verify();
    }

    @Test
    void shouldReturnEmptyWhenFailFastDisabledAndConfigManagerDown() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ConfigManagerProperties properties = defaultProperties();
        properties.setFailFast(false);

        server.expect(once(), requestTo("http://localhost:8090/api/config/v1/registry/atos-sign"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        ConfigManagerClient client = new ConfigManagerClient(properties, "atos-sign", "", restTemplate);

        TenantConfigurationDTO dto = client.fetchTenantConfiguration();

        assertThat(dto.tenants()).isEmpty();
        assertThat(dto.propertiesPerTenant()).isEmpty();
        server.verify();
    }

    private ConfigManagerProperties defaultProperties() {
        ConfigManagerProperties properties = new ConfigManagerProperties();
        properties.setBaseUrl("http://localhost:8090");
        properties.setAutoCreateApplication(true);
        properties.setRegistryPath("/api/config/v1/registry/{applicationName}");
        properties.setApplicationPath("/api/config/v1/application");
        return properties;
    }
}