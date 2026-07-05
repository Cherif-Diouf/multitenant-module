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
 * Filtre d'application <b>post-authentification</b> du contexte tenant.
 * <p>
 * Enregistré après Spring Security (par défaut ordre {@code 10}) : le {@code SecurityContext}
 * est peuplé quand il s'exécute. Il ré-exécute la chaîne de {@link TenantResolver}, soumet le
 * résultat au {@link TenantAccessValidator} de l'application, puis :
 * <ul>
 *   <li>{@code PROCEED} : vérifie que le tenant existe dans le registre (sinon 403) et
 *       positionne le {@link TenantContext} ;</li>
 *   <li>{@code PROCEED_WITHOUT_TENANT} : laisse passer sans exiger de tenant ;</li>
 *   <li>{@code REJECT} : répond avec le statut et le message décidés par le validateur.</li>
 * </ul>
 * Le nettoyage du contexte est garanti dans un bloc {@code finally}.
 */
@Slf4j
public class TenantEnforcementFilter extends OncePerRequestFilter {

    private final TenantContext tenantContext;
    private final TenantService tenantService;
    private final List<TenantResolver> resolvers;
    private final TenantAccessValidator accessValidator;
    private final List<String> excludePathPatterns;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public TenantEnforcementFilter(TenantContext tenantContext,
                                   TenantService tenantService,
                                   List<TenantResolver> resolvers,
                                   TenantAccessValidator accessValidator,
                                   List<String> excludePathPatterns) {
        this.tenantContext = tenantContext;
        this.tenantService = tenantService;
        this.resolvers = resolvers;
        this.accessValidator = accessValidator;
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
            String tenantName = ResolverChain.resolve(resolvers, request).orElse(null);
            TenantAccessDecision decision = accessValidator.validate(request, tenantName);

            switch (decision.action()) {
                case REJECT -> {
                    log.warn("Tenant access rejected ({} {}): {} [path={}]",
                            decision.statusCode(), tenantName, decision.message(), request.getServletPath());
                    response.sendError(decision.statusCode(), decision.message());
                    return;
                }
                case PROCEED_WITHOUT_TENANT ->
                        log.trace("Proceeding without tenant [path={}]", request.getServletPath());
                case PROCEED -> {
                    if (tenantName == null || tenantName.isBlank()) {
                        log.warn("Validator returned PROCEED but no tenant was resolved [path={}]",
                                request.getServletPath());
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing tenant");
                        return;
                    }
                    Optional<Tenant> tenant = ResolverChain.findTenantByName(tenantService, tenantName);
                    if (tenant.isEmpty()) {
                        log.warn("Tenant '{}' resolved but unknown in registry — request rejected [path={}]",
                                tenantName, request.getServletPath());
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Unknown tenant: " + tenantName);
                        return;
                    }
                    tenantContext.setCurrentTenant(tenant.get());
                    log.debug("Tenant context set: {} [path={}]", tenantName, request.getServletPath());
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            tenantContext.clear();
        }
    }
}
