package net.atos.digital.multitenant.resolution;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import net.atos.digital.multitenant.tenant.Tenant;
import net.atos.digital.multitenant.tenant.TenantContext;
import net.atos.digital.multitenant.tenant.TenantService;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Filtre de résolution <b>pré-authentification</b> (best-effort).
 * <p>
 * Enregistré très tôt dans la chaîne servlet (par défaut ordre {@code -200}, donc avant
 * Spring Security à {@code -100}) afin que les composants qui accèdent à la base de données
 * <i>pendant</i> l'authentification (ex. résolution des permissions par rôle) opèrent déjà
 * sur le bon schéma tenant.
 * <p>
 * Ce filtre ne rejette jamais une requête : il positionne le {@link TenantContext} si un
 * {@link TenantResolver} aboutit et que le tenant existe dans le registre, sinon il laisse
 * passer. La validation d'accès et le rejet éventuel sont assurés en aval par le
 * {@link TenantEnforcementFilter} une fois le {@code SecurityContext} peuplé.
 * <p>
 * Le nettoyage du contexte est garanti dans un bloc {@code finally}.
 */
@Slf4j
public class TenantResolutionFilter extends OncePerRequestFilter {

    private final TenantContext tenantContext;
    private final TenantService tenantService;
    private final List<TenantResolver> resolvers;
    private final List<String> excludePathPatterns;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public TenantResolutionFilter(TenantContext tenantContext,
                                  TenantService tenantService,
                                  List<TenantResolver> resolvers,
                                  List<String> excludePathPatterns) {
        this.tenantContext = tenantContext;
        this.tenantService = tenantService;
        this.resolvers = resolvers;
        this.excludePathPatterns = excludePathPatterns == null ? List.of() : excludePathPatterns;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return excludePathPatterns.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            try {
                resolveAndSetTenant(request);
            } catch (Exception ex) {
                // Best-effort : une erreur de résolution ne doit jamais bloquer la requête ici.
                log.warn("Pre-auth tenant resolution failed for {} {}: {}",
                        request.getMethod(), request.getRequestURI(), ex.getMessage());
            }
            filterChain.doFilter(request, response);
        } finally {
            tenantContext.clear();
        }
    }

    private void resolveAndSetTenant(HttpServletRequest request) {
        Optional<String> tenantName = ResolverChain.resolve(resolvers, request);
        if (tenantName.isEmpty()) {
            log.trace("No tenant resolved (pre-auth) for {} {}", request.getMethod(), request.getRequestURI());
            return;
        }

        Optional<Tenant> tenant = ResolverChain.findTenantByName(tenantService, tenantName.get());
        if (tenant.isPresent()) {
            tenantContext.setCurrentTenant(tenant.get());
            log.debug("Pre-auth tenant context set: {} (schema={})",
                    tenant.get().name(), tenant.get().schemaName());
        } else {
            log.debug("Tenant '{}' resolved (pre-auth) but not found in registry — context left empty",
                    tenantName.get());
        }
    }
}
