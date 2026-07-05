package net.atos.digital.multitenant.resolution;

import jakarta.servlet.http.HttpServletRequest;
import net.atos.digital.multitenant.utils.ProfileConstants;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;
import java.util.Optional;

/**
 * Résolveur fourni par la librairie : lit le header {@code X-TENANT}.
 * <p>
 * Le nom est normalisé en minuscules (les schémas PostgreSQL sont toujours en minuscules).
 * Prioritaire par défaut ({@link #ORDER}) sur les résolveurs applicatifs : un header explicite
 * (front multi-organisation, appel M2M, superadmin ciblant un tenant) l'emporte sur toute
 * résolution implicite. La légitimité de l'appelant à utiliser ce header est vérifiée en aval
 * par le {@link TenantAccessValidator}.
 * <p>
 * Désactivable via {@code multitenant.filter.header-resolver-enabled=false}.
 */
public class HeaderTenantResolver implements TenantResolver {

    /** Ordre par défaut : passe avant les résolveurs applicatifs (ordre 0). */
    public static final int ORDER = -100;

    @Override
    public Optional<String> resolveTenantName(HttpServletRequest request) {
        String headerTenant = request.getHeader(ProfileConstants.X_TENANT);
        if (StringUtils.isBlank(headerTenant)) {
            return Optional.empty();
        }
        return Optional.of(headerTenant.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
