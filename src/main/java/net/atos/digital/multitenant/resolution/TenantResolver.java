package net.atos.digital.multitenant.resolution;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;

import java.util.Optional;

/**
 * SPI de résolution du tenant courant à partir d'une requête HTTP entrante.
 * <p>
 * La librairie interroge tous les beans {@code TenantResolver} du contexte, triés par
 * {@link #getOrder()} croissant, et retient le <b>premier</b> résultat non vide.
 * <p>
 * La librairie fournit uniquement {@link HeaderTenantResolver} (header {@code X-TENANT}).
 * Les applications enregistrent leurs propres stratégies (claim JWT, sous-domaine,
 * segment d'URL, etc.) en déclarant un bean implémentant cette interface — aucune
 * hypothèse n'est faite sur le fournisseur d'identité.
 * <p>
 * Contrat :
 * <ul>
 *   <li>Retourner le <b>nom du tenant</b> (tel que connu du registre), pas le schéma.</li>
 *   <li>Retourner {@link Optional#empty()} si la stratégie ne s'applique pas à la requête
 *       (la main passe alors au résolveur suivant).</li>
 *   <li>Ne jamais lever d'exception pour un simple échec de résolution.</li>
 *   <li>Un résolveur peut être appelé <b>avant</b> Spring Security (filtre pré-authentification) :
 *       il ne doit pas supposer que le {@code SecurityContext} est peuplé.</li>
 * </ul>
 * <p>
 * L'<b>autorisation</b> d'accéder au tenant résolu n'est pas du ressort du résolveur :
 * elle est déléguée au {@link TenantAccessValidator} après authentification.
 */
@FunctionalInterface
public interface TenantResolver extends Ordered {

    /**
     * Tente de résoudre le nom du tenant pour la requête donnée.
     *
     * @param request la requête HTTP entrante
     * @return le nom du tenant, ou {@code Optional.empty()} si cette stratégie ne s'applique pas
     */
    Optional<String> resolveTenantName(HttpServletRequest request);

    @Override
    default int getOrder() {
        return 0;
    }
}
