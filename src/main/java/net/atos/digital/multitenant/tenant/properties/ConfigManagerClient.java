package net.atos.digital.multitenant.tenant.properties;
import lombok.extern.slf4j.Slf4j;
import net.atos.digital.multitenant.properties.ConfigManagerProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.config.ConfigDataLocation;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    // ── Cycle de vie des tenants (écriture) ─────────────────────────────────

    /**
     * Crée un tenant pour l'application courante sur le config-manager.
     * Sémantique « create-or-get » : si le tenant existe déjà (409), son identifiant
     * existant est retourné.
     *
     * @return l'identifiant du tenant côté config-manager
     */
    public String createTenant(String tenantName, String displayName) {
        Map<String, String> body = Map.of(
                "tenantName", tenantName,
                "displayName", displayName != null ? displayName : tenantName);
        try {
            Map<String, Object> response = restTemplate.exchange(
                    tenantsCollectionUrl(), HttpMethod.POST, new HttpEntity<>(body),
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    }).getBody();
            String tenantId = response != null ? Objects.toString(response.get("id"), null) : null;
            log.info("Config-manager tenant created: name={}, id={}", tenantName, tenantId);
            return tenantId;
        } catch (HttpClientErrorException.Conflict conflict) {
            log.info("Config-manager tenant '{}' already exists — returning existing id.", tenantName);
            String existingId = findTenantIdByName(tenantName);
            if (existingId == null) {
                throw new IllegalStateException(
                        "Tenant '" + tenantName + "' reported as existing (409) but not found via the API");
            }
            return existingId;
        }
    }

    /** Liste les tenants de l'application courante ({@code id}, {@code tenantName}, …). */
    public List<Map<String, Object>> listTenants() {
        List<Map<String, Object>> tenants = restTemplate.exchange(
                tenantsCollectionUrl(), HttpMethod.GET, null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {
                }).getBody();
        return tenants != null ? tenants : Collections.emptyList();
    }

    /** Retourne l'identifiant du tenant portant ce nom, ou {@code null} s'il n'existe pas. */
    public String findTenantIdByName(String tenantName) {
        return listTenants().stream()
                .filter(t -> tenantName.equals(t.get("tenantName")))
                .map(t -> Objects.toString(t.get("id"), null))
                .findFirst()
                .orElse(null);
    }

    /** Lit les propriétés (clé/valeur) d'un tenant. */
    public Map<String, String> getTenantProperties(String tenantId) {
        Map<String, String> propertiesMap = restTemplate.exchange(
                tenantPropertiesUrl(tenantId), HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, String>>() {
                }).getBody();
        return propertiesMap != null ? propertiesMap : Collections.emptyMap();
    }

    /** Remplace les propriétés (clé/valeur) d'un tenant. */
    public void updateTenantProperties(String tenantId, Map<String, String> tenantProperties) {
        Map<String, Object> body = Map.of("properties", tenantProperties);
        restTemplate.put(tenantPropertiesUrl(tenantId), body);
        log.info("Config-manager tenant properties updated: tenantId={}", tenantId);
    }

    /** Supprime un tenant de l'application courante sur le config-manager. */
    public void deleteTenant(String tenantId) {
        restTemplate.delete(tenantsCollectionUrl() + "/" + tenantId);
        log.info("Config-manager tenant deleted: id={}", tenantId);
    }

    private String tenantsCollectionUrl() {
        return resolveUrl(properties.getApplicationPath()) + "/" + applicationName + "/tenants";
    }

    private String tenantPropertiesUrl(String tenantId) {
        return resolveUrl(properties.getTenantsPath()) + "/" + tenantId + "/properties";
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