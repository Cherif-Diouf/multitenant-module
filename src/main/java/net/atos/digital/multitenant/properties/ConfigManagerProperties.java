package net.atos.digital.multitenant.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "multitenant.config-manager")
@Data
public class ConfigManagerProperties {

    /**
     * Base URL of the config-manager API, e.g. http://localhost:8090
     */
    private String baseUrl;

    /**
     * Relative or absolute URL used to load tenant registry by application name.
     */
    private String registryPath = "/api/config/v1/registry/{applicationName}";

    /**
     * Relative or absolute URL used to create an application when missing.
     */
    private String applicationPath = "/api/config/v1/applications";

    /**
     * Relative or absolute base URL of tenant resources (properties read/write).
     */
    private String tenantsPath = "/api/config/v1/tenants";

    /**
     * If true, the library creates the application when registry returns 404.
     */
    private boolean autoCreateApplication = true;

    /**
     * If true, startup fails when config-manager is unavailable.
     */
    private boolean failFast = true;

    /**
     * Optional display name sent during application creation.
     */
    private String displayName;

    /**
     * Optional description sent during application creation.
     */
    private String description;
}