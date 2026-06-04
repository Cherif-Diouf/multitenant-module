package net.atos.digital.multitenant.annotations;

import net.atos.digital.multitenant.configurations.SidebarConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import({SidebarConfiguration.class})
public @interface EnableSidebar {
}
