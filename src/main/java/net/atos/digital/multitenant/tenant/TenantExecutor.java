package net.atos.digital.multitenant.tenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.atos.digital.multitenant.TenantException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Primitive d'exécution de code dans le contexte d'un tenant donné, ou pour chaque tenant.
 * <p>
 * Remplace le motif manuel répété dans les applications :
 * <pre>{@code
 * try {
 *     tenantContext.setCurrentTenant(tenant);
 *     ...
 * } finally {
 *     tenantContext.clear();
 * }
 * }</pre>
 * avec deux garanties supplémentaires :
 * <ul>
 *   <li>le contexte <b>précédent</b> est restauré en sortie (les appels imbriqués et les
 *       itérations cross-tenant au sein d'une requête tenant-scopée ne perdent pas le
 *       contexte de l'appelant) ;</li>
 *   <li>la restauration est garantie par {@code finally}, même en cas d'exception.</li>
 * </ul>
 * Typiquement utilisé par les traitements plateforme (agrégations cross-tenant, administration)
 * et les migrations/batchs.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantExecutor {

    private final TenantContext tenantContext;
    private final TenantService tenantService;

    /** Exécute l'action dans le contexte du tenant donné, puis restaure le contexte précédent. */
    public void runAs(Tenant tenant, Runnable action) {
        callAs(tenant, () -> {
            action.run();
            return null;
        });
    }

    /** Exécute l'action dans le contexte du tenant nommé (résolu depuis le registre). */
    public void runAs(String tenantName, Runnable action) {
        runAs(requireTenant(tenantName), action);
    }

    /** Variante avec valeur de retour. */
    public <T> T callAs(Tenant tenant, Supplier<T> action) {
        Tenant previous = tenantContext.getCurrentTenant();
        try {
            tenantContext.setCurrentTenant(tenant);
            return action.get();
        } finally {
            if (previous != null) {
                tenantContext.setCurrentTenant(previous);
            } else {
                tenantContext.clear();
            }
        }
    }

    /** Variante avec valeur de retour, tenant résolu par nom depuis le registre. */
    public <T> T callAs(String tenantName, Supplier<T> action) {
        return callAs(requireTenant(tenantName), action);
    }

    /**
     * Exécute l'action pour chaque tenant disponible (y compris le schéma par défaut
     * s'il fait partie des tenants du registre). Une erreur sur un tenant est loggée
     * et n'interrompt pas les autres.
     */
    public void forEachTenant(Consumer<Tenant> action) {
        forEach(tenantService.getAllAvailableTenants(), action);
    }

    /**
     * Exécute l'action pour chaque tenant « schedulable » : respecte
     * {@code schema-configuration.include-default-schema-in-scheduling}.
     */
    public void forEachSchedulableTenant(Consumer<Tenant> action) {
        forEach(tenantService.getSchedulableTenants(), action);
    }

    private void forEach(List<Tenant> tenants, Consumer<Tenant> action) {
        for (Tenant tenant : tenants) {
            try {
                runAs(tenant, () -> action.accept(tenant));
            } catch (Exception ex) {
                log.error("Cross-tenant execution failed for tenant '{}' — continuing with next tenant",
                        tenant.name(), ex);
            }
        }
    }

    private Tenant requireTenant(String tenantName) {
        return tenantService.getAllAvailableTenants().stream()
                .filter(t -> t.name().equalsIgnoreCase(tenantName))
                .findFirst()
                .orElseThrow(() -> new TenantException("Tenant with name " + tenantName + " does not exist"));
    }
}
