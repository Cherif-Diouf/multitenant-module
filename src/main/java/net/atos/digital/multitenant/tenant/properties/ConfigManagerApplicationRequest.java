package net.atos.digital.multitenant.tenant.properties;
public record ConfigManagerApplicationRequest(
        String name,
        String displayName,
        String description
) {
}