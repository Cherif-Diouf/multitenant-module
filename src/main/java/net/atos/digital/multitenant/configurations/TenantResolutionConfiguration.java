package net.atos.digital.multitenant.configurations;

import net.atos.digital.multitenant.properties.MultitenantFilterProperties;
import net.atos.digital.multitenant.properties.SchemaConfigurationProperties;
import net.atos.digital.multitenant.resolution.HeaderTenantResolver;
import net.atos.digital.multitenant.resolution.RequireTenantAccessValidator;
import net.atos.digital.multitenant.resolution.TenantAccessValidator;
import net.atos.digital.multitenant.resolution.TenantEnforcementFilter;
import net.atos.digital.multitenant.resolution.TenantResolutionFilter;
import net.atos.digital.multitenant.resolution.TenantResolver;
import net.atos.digital.multitenant.tenant.TenantContext;
import net.atos.digital.multitenant.tenant.TenantService;
import net.atos.digital.multitenant.utils.ProfileConstants;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.ArrayList;
import java.util.List;

/**
 * Mode {@code resolver} du filtrage tenant ({@code multitenant.filter.mode=resolver}).
 * <p>
 * Remplace le {@code TenantFilter} historique par :
 * <ul>
 *   <li>un filtre <b>pré-authentification</b> (best-effort) pour que les accès BDD réalisés
 *       pendant l'authentification s'exécutent sur le bon schéma ;</li>
 *   <li>un filtre <b>post-authentification</b> qui applique la décision du
 *       {@link TenantAccessValidator} de l'application.</li>
 * </ul>
 * Les stratégies d'identification du tenant sont des beans {@link TenantResolver} : la librairie
 * fournit le résolveur {@code X-TENANT}, l'application ajoute les siens (claim JWT, sous-domaine…).
 */
@Configuration(proxyBeanMethods = false)
@Profile(ProfileConstants.MULTI_SCHEMA_OR_MULTITENANT)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "multitenant.filter", name = "mode", havingValue = "resolver")
public class TenantResolutionConfiguration {

    /** Exclusions techniques toujours appliquées au filtre d'enforcement. */
    private static final List<String> TECHNICAL_EXCLUSIONS = List.of(
            "/swagger-ui.html", "/swagger-ui/**", "/swagger-resources/**",
            "/v2/api-docs", "/v2/api-docs/**", "/v3/api-docs", "/v3/api-docs/**",
            "/webjars/**", "/configuration/security", "/configuration/ui", "/manage/**"
    );

    @Bean
    @ConditionalOnProperty(prefix = "multitenant.filter", name = "header-resolver-enabled",
            havingValue = "true", matchIfMissing = true)
    public HeaderTenantResolver headerTenantResolver() {
        return new HeaderTenantResolver();
    }

    @Bean
    @ConditionalOnMissingBean(TenantAccessValidator.class)
    public TenantAccessValidator requireTenantAccessValidator() {
        return new RequireTenantAccessValidator();
    }

    @Bean
    public FilterRegistrationBean<TenantResolutionFilter> tenantResolutionFilterRegistration(
            TenantContext tenantContext,
            TenantService tenantService,
            ObjectProvider<TenantResolver> resolvers,
            MultitenantFilterProperties filterProperties) {

        TenantResolutionFilter filter = new TenantResolutionFilter(
                tenantContext, tenantService, sortedResolvers(resolvers),
                filterProperties.getPreAuthExcludePaths());

        FilterRegistrationBean<TenantResolutionFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(filterProperties.getPreAuthOrder());
        registration.addUrlPatterns("/*");
        registration.setEnabled(filterProperties.isPreAuthEnabled());
        return registration;
    }

    @Bean
    public FilterRegistrationBean<TenantEnforcementFilter> tenantEnforcementFilterRegistration(
            TenantContext tenantContext,
            TenantService tenantService,
            ObjectProvider<TenantResolver> resolvers,
            TenantAccessValidator accessValidator,
            SchemaConfigurationProperties schemaConfigurationProperties,
            MultitenantFilterProperties filterProperties,
            @Value("${management.endpoints.web.base-path:/actuator}") String actuatorBasePath) {

        List<String> exclusions = new ArrayList<>(
                schemaConfigurationProperties.getFilters().excludePathsFilter());
        exclusions.add(actuatorBasePath + "/**");
        exclusions.addAll(TECHNICAL_EXCLUSIONS);

        TenantEnforcementFilter filter = new TenantEnforcementFilter(
                tenantContext, tenantService, sortedResolvers(resolvers), accessValidator, exclusions);

        FilterRegistrationBean<TenantEnforcementFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(filterProperties.getEnforcementOrder());
        registration.addUrlPatterns("/*");
        return registration;
    }

    private static List<TenantResolver> sortedResolvers(ObjectProvider<TenantResolver> resolvers) {
        return resolvers.orderedStream().toList();
    }
}
