package net.atos.digital.multitenant.tenant.appevent;

import lombok.Getter;
import net.atos.digital.multitenant.tenant.Tenant;
import org.springframework.context.ApplicationEvent;

@Getter
public class MultitenantEventWrapper<T> extends ApplicationEvent {

    private final Tenant tenant;
    private final T originalEvent;
    private final long wrapperCreationTime;

    public MultitenantEventWrapper(Tenant tenant, T originalEvent) {
        super(getEventSource(originalEvent));
        if (tenant == null) throw new IllegalArgumentException("Tenant cannot be null");
        if (originalEvent == null) throw new IllegalArgumentException("Original event cannot be null");
        this.tenant = tenant;
        this.originalEvent = originalEvent;
        this.wrapperCreationTime = System.currentTimeMillis();
    }

    private static Object getEventSource(Object event) {
        if (event instanceof ApplicationEvent) return ((ApplicationEvent) event).getSource();
        return event;
    }

    @Override
    public String toString() {
        return String.format("MultitenantEventWrapper{tenant='%s', originalEvent='%s', creationTime=%d}",
                tenant.name(),
                originalEvent.getClass().getSimpleName(),
                wrapperCreationTime);
    }
}
