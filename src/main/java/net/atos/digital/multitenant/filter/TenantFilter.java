/*
 *
 *  * Copyright © 2019-2023 Worldline France. All Rights Reserved.
 *
 */

package net.atos.digital.multitenant.filter;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import net.atos.digital.multitenant.properties.SchemaConfigurationProperties;
import net.atos.digital.multitenant.tenant.Tenant;
import net.atos.digital.multitenant.tenant.TenantContext;
import net.atos.digital.multitenant.tenant.properties.TenantConfigurationRegistry;
import net.atos.digital.multitenant.tenant.properties.TenantData;
import net.atos.digital.multitenant.utils.ProfileConstants;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Filtre historique basé exclusivement sur le header {@code X-TENANT}.
 * <p>
 * Actif par défaut ({@code multitenant.filter.mode=legacy}). Les applications qui ont besoin
 * d'une résolution de tenant personnalisée (JWT, sous-domaine, etc.) doivent passer en
 * {@code multitenant.filter.mode=resolver} : ce filtre est alors remplacé par la paire
 * {@code TenantResolutionFilter}/{@code TenantEnforcementFilter} pilotée par le SPI
 * {@link net.atos.digital.multitenant.resolution.TenantResolver}.
 */
@Profile(ProfileConstants.MULTI_SCHEMA_OR_MULTITENANT)
@ConditionalOnProperty(prefix = "multitenant.filter", name = "mode", havingValue = "legacy", matchIfMissing = true)
@Slf4j
@Component
public class TenantFilter extends OncePerRequestFilter {
    private final TenantContext tenantContext;
    private final SchemaConfigurationProperties schemaConfigurationProperties;
    private List<String> excludeUrlPatterns;
    private AntPathMatcher pathMatcher = new AntPathMatcher();
    private static final String PUBLIC = "public";
    private final TenantConfigurationRegistry tenantConfigurationRegistry;


    public TenantFilter(TenantContext tenantContext, @Value("${management.endpoints.web.base-path:/actuator}") String actuatorUrl, SchemaConfigurationProperties schemaConfigurationProperties, TenantConfigurationRegistry tenantConfigurationRegistry) {
        this.tenantContext = tenantContext;
        this.excludeUrlPatterns = new ArrayList<>(schemaConfigurationProperties.getFilters().excludePathsFilter());
        this.schemaConfigurationProperties = schemaConfigurationProperties;
        this.tenantConfigurationRegistry = tenantConfigurationRegistry;

        // add specific exclusions
        this.excludeUrlPatterns.add(actuatorUrl + "/**");
        this.excludeUrlPatterns.add("/swagger-ui.html*");
        this.excludeUrlPatterns.add("/swagger-resources/**");
        this.excludeUrlPatterns.add("/webjars/**");
        this.excludeUrlPatterns.add("/v2/api-docs*");
        this.excludeUrlPatterns.add("swagger-ui/**");
        this.excludeUrlPatterns.add("/swagger-ui/**");
        this.excludeUrlPatterns.add("swagger-ui.html/**");
        this.excludeUrlPatterns.add("/swagger-ui.html/**");
        this.excludeUrlPatterns.add("/index.html/**");
        this.excludeUrlPatterns.add("index.html/**");
        this.excludeUrlPatterns.add("v3/api-docs/**");
        this.excludeUrlPatterns.add("/v3/api-docs/**");
        this.excludeUrlPatterns.add("/v3/api-docs");
        this.excludeUrlPatterns.add("/v2/api-docs");
        this.excludeUrlPatterns.add("/configuration/security");
        this.excludeUrlPatterns.add("/configuration/ui");
        this.excludeUrlPatterns.add("/manage/**");
        this.excludeUrlPatterns.add("/api/*/v3/api-docs");
        this.excludeUrlPatterns.add("/api/*/v3/api-docs/**");
        this.excludeUrlPatterns.add("/api/*/v2/api-docs");
        this.excludeUrlPatterns.add("/api/v3/api-docs/**");
        this.excludeUrlPatterns.add("/api/v3/api-docs");

    }

    @Override
    public void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain filterChain) throws ServletException, IOException {
        String tenantName = req.getHeader(ProfileConstants.X_TENANT);

        if (tenantConfigurationRegistry.isMultitenantActive()) {
            filterForMultitenant(tenantName, req, res, filterChain);
        } else {
            filterForMultiSchema(tenantName, req, res, filterChain);
        }

    }

    @Override
    public boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        return excludeUrlPatterns.stream()
            .anyMatch(p -> pathMatcher.match(request.getContextPath() + p, request.getRequestURI()));
    }

    private void filterForMultitenant(String currentTenantName, HttpServletRequest req, HttpServletResponse res, FilterChain filterChain) throws IOException, ServletException {
        if (StringUtils.isBlank(currentTenantName)) {
            log.error("filterForMultitenant Missing Tenant header for uri {}", req.getRequestURI().replaceAll("[\r\n]",""));
            res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing Tenant header");
            return;
        }
        Tenant tenant = null;

        if (CollectionUtils.isNotEmpty(schemaConfigurationProperties.getSchemas())) {
            if (schemaConfigurationProperties.getSchemas().stream().anyMatch(itemSchemaConfiguration -> itemSchemaConfiguration.schemaName().equals(currentTenantName))) {
                tenant = new Tenant(TenantContext.DEFAULT_TENANT_UUID, currentTenantName, currentTenantName);
            }
        }
        if (Objects.isNull(tenant)) {
            try {
                TenantData tenantData = tenantConfigurationRegistry.findTenantByName(currentTenantName);
                if (Objects.nonNull(tenantData)) {
                    tenant = tenantData.toTenant();
                }
            } catch (RuntimeException ex) {
                log.error("filterForMultitenant Missing Tenant matching for uri {}", req.getRequestURI().replaceAll("[\r\n]",""));
                res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing Tenant matching header");
                return;
            }
        }

        if (Objects.isNull(tenant)) {
            log.error("filterForMultitenant Missing Tenant matching for uri {}", req.getRequestURI().replaceAll("[\r\n]",""));
            res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing Tenant matching header");
            return;
        }

        manageTenantFilterContext(req, res, filterChain, tenant);

    }

    private void manageTenantFilterContext(HttpServletRequest req, HttpServletResponse res, FilterChain filterChain, Tenant tenant) throws IOException, ServletException {
        if (tenantContext == null) {
            log.error("Tenant does not exist for uri {}", req.getRequestURI().replaceAll("[\r\n]",""));
            res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Tenant does not exist");
            return;
        }
        this.tenantContext.setCurrentTenant(tenant);

        log.trace("Before tenant filter. Name: {}", tenant);

        try {
            filterChain.doFilter(req, res);
        } finally {
            // Nettoyage garanti même en cas d'exception dans la chaîne :
            // sans finally, le tenant resterait attaché au thread du pool du serveur.
            log.trace("After Tenant Filter. Cleaning tenant");
            this.tenantContext.clear();
        }
    }

    private void filterForMultiSchema(String currentTenantName, HttpServletRequest req, HttpServletResponse res, FilterChain filterChain) throws IOException, ServletException {
        String schemaName;
        List<SchemaConfigurationProperties.ItemSchemaConfiguration> configuredSchemas =
                schemaConfigurationProperties.getSchemas() != null ? schemaConfigurationProperties.getSchemas() : Collections.emptyList();

        if (StringUtils.isBlank(currentTenantName) || schemaConfigurationProperties.getDefaultSchema().schemaName().equals(currentTenantName)) {
            try {
                TenantData tenantData = tenantConfigurationRegistry.findTenantByName(schemaConfigurationProperties.getDefaultSchema().schemaName());
                if (Objects.nonNull(tenantData)) {
                    schemaName = tenantData.toTenant().schemaName();
                } else {
                    schemaName = schemaConfigurationProperties.getDefaultSchema().schemaName();
                }
            } catch (RuntimeException ex) {
                schemaName = schemaConfigurationProperties.getDefaultSchema().schemaName();
            }

            log.info("Schema name: {}", schemaName);
        } else {
            schemaName = currentTenantName;
            if (configuredSchemas.stream().noneMatch(itemSchemaConfiguration -> itemSchemaConfiguration.schemaName().equals(currentTenantName))) {
                log.error("filterForMultiSchema: schema '{}' not found in configuration", currentTenantName);
                res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Schema not found in configuration");
                return;
            }
        }

        String tenantName = StringUtils.isBlank(currentTenantName)
                ? schemaConfigurationProperties.getDefaultSchema().schemaName()
                : currentTenantName;
        Tenant tenant = new Tenant(TenantContext.DEFAULT_TENANT_UUID, tenantName, schemaName);
        manageTenantFilterContext(req, res, filterChain, tenant);

    }
}