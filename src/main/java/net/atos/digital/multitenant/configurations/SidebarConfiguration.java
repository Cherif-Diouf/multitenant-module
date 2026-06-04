package net.atos.digital.multitenant.configurations;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(basePackages = "net.atos.digital.multitenant")
@ConfigurationPropertiesScan(basePackages = "net.atos.digital.multitenant.properties")
public class SidebarConfiguration {
}
