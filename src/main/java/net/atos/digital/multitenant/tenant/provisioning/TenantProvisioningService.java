package net.atos.digital.multitenant.tenant.provisioning;

import net.atos.digital.multitenant.tenant.Tenant;

/**
 * Service de provisioning d'un nouveau tenant à chaud (runtime).
 * <p>
 * Crée le schéma PostgreSQL, exécute les migrations Liquibase,
 * enregistre le tenant dans le registry et positionne le TenantContext.
 * <p>
 * L'application consommatrice peut injecter ce service pour provisionner
 * un nouveau tenant lors de la création d'une organisation.
 */
public interface TenantProvisioningService {

    /**
     * Provisionne un nouveau tenant : schéma + Liquibase + enregistrement registry.
     *
     * @param tenantId   UUID du tenant (fourni par le Config Manager)
     * @param tenantName Nom du tenant (= nom du schéma PostgreSQL, lowercase)
     * @return le Tenant créé (avec contexte positionné)
     * @throws IllegalArgumentException si le nom du schéma est invalide
     * @throws IllegalStateException    si la migration Liquibase échoue
     */
    Tenant provision(String tenantId, String tenantName);

    /**
     * Dé-provisionne un tenant : suppression du schéma PostgreSQL (DROP … CASCADE)
     * et retrait du tenant du registry local.
     * <p>
     * Opération destructrice et définitive — typiquement utilisée pour la suppression
     * d'une organisation ou la compensation (rollback) d'un provisioning échoué.
     * Le désenregistrement côté Config Manager reste à la charge de l'appelant
     * (via {@code ConfigManagerClient.deleteTenant}), car le tenant peut exister
     * sur le Config Manager sans schéma local et inversement.
     *
     * @param tenantName Nom du tenant (= nom du schéma PostgreSQL, lowercase)
     * @throws IllegalArgumentException si le nom du schéma est invalide
     */
    void deprovision(String tenantName);

    /**
     * Nettoie le TenantContext courant.
     * À appeler dans un bloc finally après le provisioning si nécessaire.
     */
    void clearContext();
}
