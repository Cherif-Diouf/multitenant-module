package net.atos.digital.multitenant.tenant.hibernate;

import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import net.atos.digital.multitenant.utils.ProfileConstants;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnClass(SessionFactoryImplementor.class)
@Profile(ProfileConstants.MULTI_SCHEMA_OR_MULTITENANT)
public class HibernateCacheService {

    private final EntityManagerFactory emf;

    public void evictAllRegions() {
        SessionFactoryImplementor sfi = emf.unwrap(SessionFactoryImplementor.class);
        sfi.getCache().evictAllRegions();
    }
}