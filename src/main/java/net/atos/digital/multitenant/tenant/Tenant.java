

package net.atos.digital.multitenant.tenant;

import java.util.UUID;

public record Tenant(
    UUID id,
    String name,
    String schemaName
){}