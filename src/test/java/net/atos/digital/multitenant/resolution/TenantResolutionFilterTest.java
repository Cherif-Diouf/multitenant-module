package net.atos.digital.multitenant.resolution;

import jakarta.servlet.FilterChain;
import net.atos.digital.multitenant.tenant.Tenant;
import net.atos.digital.multitenant.tenant.TenantContext;
import net.atos.digital.multitenant.tenant.TenantService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantResolutionFilter (pré-auth)")
class TenantResolutionFilterTest {

    @Mock
    private TenantContext tenantContext;
    @Mock
    private TenantService tenantService;
    @Mock
    private FilterChain chain;

    private static final Tenant ACME = new Tenant(UUID.randomUUID(), "acme", "acme");

    @Test
    @DisplayName("tenant résolu et connu => contexte positionné puis nettoyé")
    void resolvedTenant_setsContext() throws Exception {
        when(tenantService.getAllAvailableTenants()).thenReturn(List.of(ACME));
        var filter = new TenantResolutionFilter(tenantContext, tenantService,
                List.of(r -> Optional.of("acme")), List.of());
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/documents");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, chain);

        verify(tenantContext).setCurrentTenant(ACME);
        verify(chain).doFilter(req, res);
        verify(tenantContext).clear();
    }

    @Test
    @DisplayName("aucun tenant résolu => la requête passe quand même (best-effort)")
    void noTenant_stillContinues() throws Exception {
        var filter = new TenantResolutionFilter(tenantContext, tenantService,
                List.of(r -> Optional.empty()), List.of());
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/documents");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, chain);

        verify(tenantContext, never()).setCurrentTenant(ACME);
        verify(chain).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("une exception d'un résolveur ne bloque jamais la requête")
    void resolverException_neverBlocks() throws Exception {
        var filter = new TenantResolutionFilter(tenantContext, tenantService,
                List.of(r -> {
                    throw new IllegalStateException("boom");
                }), List.of());
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/documents");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, chain);

        verify(chain).doFilter(req, res);
        verify(tenantContext).clear();
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("chemin exclu => filtre non appliqué")
    void excludedPath_isSkipped() {
        var filter = new TenantResolutionFilter(tenantContext, tenantService,
                List.of(r -> Optional.of("acme")), List.of("/platform/**"));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/platform/orgs");
        req.setServletPath("/platform/orgs");

        assertThat(filter.shouldNotFilter(req)).isTrue();
    }
}
