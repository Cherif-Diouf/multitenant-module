package net.atos.digital.multitenant.tenant.hibernate.schema;

import net.atos.digital.multitenant.properties.SchemaConfigurationProperties;
import net.atos.digital.multitenant.tenant.properties.TenantConfigurationRegistry;
import net.atos.digital.multitenant.tenant.properties.TenantData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchemaCreatorServiceTest {

    private static final UUID UUID_A = UUID.randomUUID();
    private static final UUID UUID_B = UUID.randomUUID();

    @Mock
    private TenantConfigurationRegistry registry;

    private SchemaConfigurationProperties schemaProps;
    private SchemaCreatorService service;

    @BeforeEach
    void setUp() {
        schemaProps = new SchemaConfigurationProperties();
        service = new SchemaCreatorService(registry, schemaProps);
    }

    @Test
    void getAllSchemasInMultitenantModeReturnsTenantSchemas() {
        when(registry.isMultitenantActive()).thenReturn(true);
        when(registry.getTenants()).thenReturn(List.of(
                new TenantData(UUID_A, "acme", "ACME", true),
                new TenantData(UUID_B, "beta", "Beta Corp", true)));

        assertThat(service.getAllSchemas()).containsExactlyInAnyOrder("acme", "beta");
    }

    @Test
    void getAllSchemasReturnsEmptyWhenNoTenantAndNoStaticSchema() {
        when(registry.isMultitenantActive()).thenReturn(true);
        when(registry.getTenants()).thenReturn(List.of());

        assertThat(service.getAllSchemas()).isEmpty();
    }

    @Test
    void getAllSchemasIncludesConfiguredStaticSchema() {
        when(registry.isMultitenantActive()).thenReturn(false);
        when(registry.getTenants()).thenReturn(List.of());
        schemaProps.setSchemas(List.of(
                new SchemaConfigurationProperties.ItemSchemaConfiguration(List.of(), true, "reporting", List.of())
        ));
        schemaProps.setDefaultSchema(new SchemaConfigurationProperties.ItemSchemaConfiguration(List.of(), true, "public", List.of()));

        assertThat(service.getAllSchemas()).contains("reporting", "public");
    }

    @Test
    void getAllUpcomingSchemasAlwaysReturnsEmpty() {
        assertThat(service.getAllUpcomingSchemas()).isEmpty();
    }

    @Test
    void getAllSchemasAvoidsDuplicates() {
        when(registry.isMultitenantActive()).thenReturn(true);
        when(registry.getTenants()).thenReturn(List.of(
                new TenantData(UUID_A, "acme", "ACME", true),
                new TenantData(UUID_A, "acme", "ACME duplicate", true)));
        schemaProps.setSchemas(List.of(
                new SchemaConfigurationProperties.ItemSchemaConfiguration(List.of(), true, "acme", List.of())
        ));

        assertThat(service.getAllSchemas()).containsOnlyOnce("acme");
    }
}