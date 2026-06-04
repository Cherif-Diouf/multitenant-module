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
     * Nettoie le TenantContext courant.
     * À appeler dans un bloc finally après le provisioning si nécessaire.
     */
    void clearContext();
}
