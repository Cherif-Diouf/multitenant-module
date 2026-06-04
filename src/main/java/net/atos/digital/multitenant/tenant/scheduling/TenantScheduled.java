

package net.atos.digital.multitenant.tenant.scheduling;

import org.springframework.scheduling.annotation.Scheduled;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation that wraps {@link Scheduled} to handle multitenancy
 *
 * @see TenantScheduledAnnotationBeanPostProcessor
 */

@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TenantScheduled {
    Scheduled scheduled();
}
