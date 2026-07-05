package net.atos.digital.multitenant.resolution;

import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.UtilityClass;
import net.atos.digital.multitenant.tenant.Tenant;
import net.atos.digital.multitenant.tenant.TenantService;

import java.util.List;
import java.util.Optional;

/**
 * Logique partagée entre {@link TenantResolutionFilter} et {@link TenantEnforcementFilter} :
 * parcours de la chaîne de résolveurs et recherche du tenant dans le registre.
 */
@UtilityClass
class ResolverChain {

    /** Retourne le premier résultat non vide de la chaîne (résolveurs déjà triés par ordre). */
    static Optional<String> resolve(List<TenantResolver> resolvers, HttpServletRequest request) {
        for (TenantResolver resolver : resolvers) {
            Optional<String> resolved = resolver.resolveTenantName(request);
            if (resolved.isPresent() && !resolved.get().isBlank()) {
                return resolved;
            }
        }
        return Optional.empty();
    }

    /** Recherche insensible à la casse dans les tenants connus du registre. */
    static Optional<Tenant> findTenantByName(TenantService tenantService, String tenantName) {
        return tenantService.getAllAvailableTenants().stream()
                .filter(t -> tenantName.equalsIgnoreCase(t.name()))
                .findFirst();
    }
}
