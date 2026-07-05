package net.atos.digital.multitenant.resolution;

import jakarta.servlet.http.HttpServletRequest;

/**
 * SPI d'autorisation d'accès au tenant, appelé <b>après</b> l'authentification Spring Security
 * par le filtre d'application ({@code TenantEnforcementFilter}).
 * <p>
 * C'est ici que l'application exprime sa politique de sécurité multi-tenant :
 * « l'appelant a-t-il le droit d'opérer sur le tenant résolu (ou sans tenant) ? ».
 * Exemples de règles applicatives : valider que le header {@code X-TENANT} correspond bien
 * à une organisation du JWT de l'appelant, autoriser un administrateur plateforme à cibler
 * n'importe quel tenant, rejeter un utilisateur sans tenant sur un endpoint tenant-scoped.
 * <p>
 * Implémentation par défaut si l'application n'en fournit aucune :
 * {@link RequireTenantAccessValidator} (tenant obligatoire, mêmes garanties que le
 * filtre legacy).
 */
@FunctionalInterface
public interface TenantAccessValidator {

    /**
     * Valide l'accès de l'appelant au tenant résolu.
     *
     * @param request    la requête HTTP (le {@code SecurityContext} est peuplé à ce stade)
     * @param tenantName le nom de tenant résolu par la chaîne de {@link TenantResolver},
     *                   ou {@code null} si aucun résolveur n'a abouti
     * @return la décision d'accès
     */
    TenantAccessDecision validate(HttpServletRequest request, String tenantName);
}
