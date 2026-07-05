package net.atos.digital.multitenant.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration du mode de filtrage tenant HTTP.
 *
 * <pre>
 * multitenant:
 *   filter:
 *     mode: resolver              # legacy (défaut) | resolver
 *     pre-auth-enabled: true      # filtre best-effort avant Spring Security
 *     pre-auth-order: -200        # avant Spring Security (-100)
 *     pre-auth-exclude-paths:     # chemins ignorés par le filtre pré-auth
 *       - /platform/**
 *     enforcement-order: 10       # après Spring Security
 *     header-resolver-enabled: true
 * </pre>
 *
 * En mode {@code resolver}, le {@code TenantFilter} historique est remplacé par la paire
 * {@code TenantResolutionFilter} (pré-auth, best-effort) / {@code TenantEnforcementFilter}
 * (post-auth, validation via {@code TenantAccessValidator}). Les chemins exclus du filtre
 * d'enforcement restent pilotés par {@code schema-configuration.filters.exclude-paths-filter}
 * (complétés des exclusions techniques : actuator, swagger, api-docs).
 */
@Component
@ConfigurationProperties(prefix = "multitenant.filter")
@Data
public class MultitenantFilterProperties {

    /** {@code legacy} : TenantFilter historique (X-TENANT) ; {@code resolver} : SPI TenantResolver. */
    private String mode = "legacy";

    /** Active le filtre de résolution pré-authentification (mode resolver uniquement). */
    private boolean preAuthEnabled = true;

    /** Ordre servlet du filtre pré-auth. Doit précéder Spring Security (-100). */
    private int preAuthOrder = -200;

    /** Chemins (AntPath, servletPath) ignorés par le filtre pré-auth. */
    private List<String> preAuthExcludePaths = new ArrayList<>();

    /** Ordre servlet du filtre d'enforcement. Doit suivre Spring Security (-100). */
    private int enforcementOrder = 10;

    /** Active le résolveur fourni {@code X-TENANT} (mode resolver uniquement). */
    private boolean headerResolverEnabled = true;
}
