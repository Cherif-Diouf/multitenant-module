

package net.atos.digital.multitenant.tenant.properties;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.bind.PlaceholdersResolver;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import java.util.Map;

@Slf4j
public class CustomPlaceholdersResolver implements PlaceholdersResolver {

    private final ConfigurableEnvironment environment;

    public CustomPlaceholdersResolver(Map<String, Object> tenantProperties, Map<String, Object> systemProperties, ConfigurableEnvironment environment) {
        this.environment = environment;

        // Ajouter les sources en priorité
        MutablePropertySources propertySources = this.environment.getPropertySources();
        propertySources.addFirst(new MapPropertySource("tenantProperties", tenantProperties));
        propertySources.addAfter("tenantProperties", new MapPropertySource("systemProperties", systemProperties));

        log.debug("Added tenant and system properties to Spring environment");
    }

    @Override
    public Object resolvePlaceholders(Object value) {
        if (value instanceof String strVal) {
            String resolved = environment.resolvePlaceholders(strVal);
            log.debug("Resolved '{}' -> '{}'", strVal, resolved);
            return resolved;
        }
        return value;
    }
}

