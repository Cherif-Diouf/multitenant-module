

package net.atos.digital.multitenant.tenant.hibernate.schema;

@FunctionalInterface
public interface SchemaSelector {
    String apply(String schema);
}
