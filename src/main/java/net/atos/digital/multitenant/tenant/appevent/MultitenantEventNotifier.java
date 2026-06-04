package net.atos.digital.multitenant.tenant.appevent;

import lombok.extern.slf4j.Slf4j;
import net.atos.digital.messagebroker.appevent.annotations.EventNotifiable;
import net.atos.digital.messagebroker.appevent.models.AbstractApplicationEvent;
import net.atos.digital.messagebroker.appevent.service.EventNotifier;
import net.atos.digital.multitenant.tenant.Tenant;
import net.atos.digital.multitenant.tenant.TenantContext;
import net.atos.digital.multitenant.utils.ProfileConstants;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@ConditionalOnClass(EventNotifier.class)
@Profile(ProfileConstants.MULTI_SCHEMA_OR_MULTITENANT)
public class MultitenantEventNotifier extends EventNotifier {

    private final ExecutorService executor;
    private final TenantContext tenantContext;
    private final ApplicationEventPublisher applicationEventPublisher;

    public MultitenantEventNotifier(ExecutorService executor, TenantContext tenantContext, ApplicationEventPublisher applicationEventPublisher) {
        this.executor = executor;
        this.tenantContext = tenantContext;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    private void sendVerifyAsync(AbstractApplicationEvent applicationEvent, boolean async) {
        if (async) {
            Tenant currentTenant = tenantContext.getCurrentTenant();
            CompletableFuture.runAsync(() -> {
                try {
                    tenantContext.setCurrentTenant(currentTenant);
                    log.info("eventNotifyData sync - topicName: {}, event: {}", applicationEvent.getTopicName(), applicationEvent.getClass().getName());
                    applicationEventPublisher.publishEvent(applicationEvent);
                } finally {
                    tenantContext.clear();
                }
            }, executor).orTimeout(10, TimeUnit.MILLISECONDS);
        } else {
            log.info("eventNotifyData  - topicName: {}, event: {}", applicationEvent.getTopicName(), applicationEvent.getClass().getName());
            applicationEventPublisher.publishEvent(applicationEvent);
        }
    }

    @Override
    public void sendEvent(AbstractApplicationEvent applicationEvent, EventNotifiable eventNotifiable) {
        if (eventNotifiable.transactional()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendVerifyAsync(applicationEvent, eventNotifiable.async());
                }
            });
        } else {
            sendVerifyAsync(applicationEvent, eventNotifiable.async());
        }
    }
}
