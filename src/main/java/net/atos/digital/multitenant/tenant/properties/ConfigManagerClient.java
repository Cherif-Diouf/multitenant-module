package net.atos.digital.multitenant.tenant.properties;
import lombok.extern.slf4j.Slf4j;
import net.atos.digital.multitenant.properties.ConfigManagerProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.config.ConfigDataLocation;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import java.net.URI;
import java.util.Collections;
@Slf4j
@Component
public class ConfigManagerClient {
    private final RestTemplate restTemplate;
    private final ConfigManagerProperties properties;
    private final String applicationName;
    private final String configImport;
    @Autowired
    public ConfigManagerClient(ConfigManagerProperties properties,
                               @Value("${spring.application.name}") String applicationName,
                               @Value("${spring.config.import:}") String configImport) {
        this(properties, applicationName, configImport, new RestTemplate());
    }
    ConfigManagerClient(ConfigManagerProperties properties,
                        String applicationName,
                        String configImport,
                        RestTemplate restTemplate) {
        this.properties = properties;
        this.applicationName = applicationName;
        this.configImport = configImport;
        this.restTemplate = restTemplate;
    }
    public TenantConfigurationDTO fetchTenantConfiguration() {
        try {
            TenantConfigurationDTO dto = tryReadRegistry();
            if (dto != null) {
                return dto;
            }
            if (!properties.isAutoCreateApplication()) {
                log.warn("Config-manager registry not found for application '{}'. Auto-create disabled.", applicationName);
                return emptyDto();
            }
            createApplicationIfMissing();
            TenantConfigurationDTO refreshed = tryReadRegistry();
            return refreshed != null ? refreshed : emptyDto();
        } catch (RestClientException | IllegalArgumentException e) {
            if (properties.isFailFast()) {
                throw new IllegalStateException("Unable to initialize tenant configuration from config-manager", e);
            }
            log.error("Unable to initialize tenant configuration from config-manager: {}", e.getMessage());
            return emptyDto();
        }
    }
    private TenantConfigurationDTO tryReadRegistry() {
        try {
            TenantConfigurationDTO dto = restTemplate.getForObject(resolveRegistryUrl(), TenantConfigurationDTO.class, applicationName);
            return dto != null ? dto : emptyDto();
        } catch (HttpClientErrorException.NotFound notFound) {
            return null;
        }
    }
    private void createApplicationIfMissing() {
        ConfigManagerApplicationRequest request = new ConfigManagerApplicationRequest(
                applicationName,
                StringUtils.hasText(properties.getDisplayName()) ? properties.getDisplayName() : null,
                StringUtils.hasText(properties.getDescription()) ? properties.getDescription() : null
        );
        try {
            restTemplate.postForLocation(resolveApplicationUrl(), request);
            log.info("Config-manager application '{}' created.", applicationName);
        } catch (HttpClientErrorException clientError) {
            HttpStatusCode statusCode = clientError.getStatusCode();
            if (statusCode.value() == 409) {
                log.info("Config-manager application '{}' already exists.", applicationName);
                return;
            }
            throw clientError;
        }
    }
    private String resolveRegistryUrl() {
        return resolveUrl(properties.getRegistryPath());
    }
    private String resolveApplicationUrl() {
        return resolveUrl(properties.getApplicationPath());
    }
    private String resolveUrl(String path) {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        String baseUrl = properties.getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            baseUrl = extractBaseUrlFromConfigImport();
        }
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalArgumentException("Missing config-manager base URL. Set multitenant.config-manager.base-url or spring.config.import");
        }
        return trimTrailingSlash(baseUrl) + path;
    }
    private String extractBaseUrlFromConfigImport() {
        if (!StringUtils.hasText(configImport)) {
            return null;
        }
        ConfigDataLocation configDataLocation = ConfigDataLocation.of(configImport);
        URI uri = URI.create(configDataLocation.getNonPrefixedValue("configserver:"));
        return uri.getScheme() + "://" + uri.getAuthority();
    }
    private String trimTrailingSlash(String value) {
        int index = value.length();
        while (index > 0 && value.charAt(index - 1) == '/') {
            index--;
        }
        return value.substring(0, index);
    }
    private TenantConfigurationDTO emptyDto() {
        return new TenantConfigurationDTO(Collections.emptyList(), Collections.emptyMap());
    }
}