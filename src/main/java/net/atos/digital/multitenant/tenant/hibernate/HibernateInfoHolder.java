

package net.atos.digital.multitenant.tenant.hibernate;

import org.hibernate.boot.Metadata;

public class HibernateInfoHolder {
    private static Metadata metadata;

    private HibernateInfoHolder() {
    }

    public static Metadata getMetadata() {
        return metadata;
    }

    public static void setMetadata(Metadata metadata) {
        HibernateInfoHolder.metadata = metadata;
    }
}
