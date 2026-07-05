package net.atos.digital.multitenant.resolution;

/**
 * Décision rendue par un {@link TenantAccessValidator} pour une requête donnée.
 * <ul>
 *   <li>{@link Action#PROCEED} : le tenant résolu est légitime — le contexte tenant est positionné.</li>
 *   <li>{@link Action#PROCEED_WITHOUT_TENANT} : la requête continue sans exiger de tenant
 *       (endpoint public, appelant plateforme…). Le contexte éventuellement posé en amont
 *       n'est pas modifié.</li>
 *   <li>{@link Action#REJECT} : la requête est refusée avec le statut HTTP et le message fournis.</li>
 * </ul>
 */
public record TenantAccessDecision(Action action, int statusCode, String message) {

    public enum Action {PROCEED, PROCEED_WITHOUT_TENANT, REJECT}

    public static TenantAccessDecision proceed() {
        return new TenantAccessDecision(Action.PROCEED, 0, null);
    }

    public static TenantAccessDecision proceedWithoutTenant() {
        return new TenantAccessDecision(Action.PROCEED_WITHOUT_TENANT, 0, null);
    }

    public static TenantAccessDecision reject(int statusCode, String message) {
        return new TenantAccessDecision(Action.REJECT, statusCode, message);
    }
}
