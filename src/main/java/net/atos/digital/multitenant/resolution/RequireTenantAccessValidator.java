package net.atos.digital.multitenant.resolution;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Validateur par défaut : un tenant doit être résolu pour toute requête non exclue,
 * sinon la requête est rejetée en 400 — même comportement que le filtre legacy.
 * <p>
 * Remplacé dès que l'application déclare son propre bean {@link TenantAccessValidator}.
 */
public class RequireTenantAccessValidator implements TenantAccessValidator {

    @Override
    public TenantAccessDecision validate(HttpServletRequest request, String tenantName) {
        if (tenantName == null || tenantName.isBlank()) {
            return TenantAccessDecision.reject(HttpServletResponse.SC_BAD_REQUEST, "Missing Tenant header");
        }
        return TenantAccessDecision.proceed();
    }
}
