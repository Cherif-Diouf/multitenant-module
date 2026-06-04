

package net.atos.digital.multitenant.tenant.properties;

import java.lang.annotation.*;

@Target({ ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TenantConfigurationProperties {
    String prefix() default "";
}
