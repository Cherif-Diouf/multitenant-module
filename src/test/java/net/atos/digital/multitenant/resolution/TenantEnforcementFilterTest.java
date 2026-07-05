package net.atos.digital.multitenant.resolution;

import jakarta.servlet.FilterChain;
import net.atos.digital.multitenant.tenant.Tenant;
import net.atos.digital.multitenant.tenant.TenantContext;
import net.atos.digital.multitenant.tenant.TenantService;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantEnforcementFilter")
class TenantEnforcementFilterTest {

    @Mock
    private TenantContext tenantContext;
    @Mock
    private TenantService tenantService;
    @Mock
    private FilterChain chain;

    private static final Tenant ACME = new Tenant(UUID.randomUUID(), "acme", "acme");

    private TenantEnforcementFilter filter(TenantResolver resolver, TenantAccessValidator validator) {
        return new TenantEnforcementFilter(tenantContext, tenantService,
                List.of(resolver), validator, List.of("/excluded/**"));
    }

    @BeforeEach
    void setUp() {
        lenient().when(tenantService.getAllAvailableTenants()).thenReturn(List.of(ACME));
    }

    @Test
    @DisplayName("PROCEED avec tenant connu => contexte positionné, chaîne continuée, clear en finally")
    void proceed_knownTenant_setsContext() throws Exception {
        var filter = filter(r -> Optional.of("acme"), (r, t) -> TenantAccessDecision.proceed());
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/documents");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, chain);

        verify(tenantContext).setCurrentTenant(ACME);
        verify(chain).doFilter(req, res);
        verify(tenantContext).clear();
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("PROCEED avec tenant inconnu du registre => 403")
    void proceed_unknownTenant_returns403() throws Exception {
        var filter = filter(r -> Optional.of("ghost"), (r, t) -> TenantAccessDecision.proceed());
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/documents");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(req, res);
        verify(tenantContext, never()).setCurrentTenant(ACME);
        verify(tenantContext).clear();
    }

    @Test
    @DisplayName("REJECT => statut et message du validateur, chaîne stoppée")
    void reject_sendsValidatorStatus() throws Exception {
        var filter = filter(r -> Optional.empty(),
                (r, t) -> TenantAccessDecision.reject(403, "Tenant requis"));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/documents");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getErrorMessage()).isEqualTo("Tenant requis");
        verify(chain, never()).doFilter(req, res);
    }

    @Test
    @DisplayName("PROCEED_WITHOUT_TENANT => chaîne continuée sans positionner de tenant")
    void proceedWithoutTenant_continues() throws Exception {
        var filter = filter(r -> Optional.empty(), (r, t) -> TenantAccessDecision.proceedWithoutTenant());
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/public");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, chain);

        verify(tenantContext, never()).setCurrentTenant(ACME);
        verify(chain).doFilter(req, res);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("clear() est appelé même si la chaîne lève une exception")
    void clear_calledOnException() throws Exception {
        var filter = filter(r -> Optional.of("acme"), (r, t) -> TenantAccessDecision.proceed());
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/documents");
        MockHttpServletResponse res = new MockHttpServletResponse();
        doThrow(new RuntimeException("boom")).when(chain).doFilter(req, res);

        try {
            filter.doFilterInternal(req, res, chain);
        } catch (RuntimeException expected) {
            // attendu
        }

        verify(tenantContext).clear();
    }

    @Test
    @DisplayName("chemin exclu => filtre non appliqué")
    void excludedPath_isSkipped() {
        var filter = filter(r -> Optional.of("acme"), (r, t) -> TenantAccessDecision.proceed());
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/excluded/health");
        req.setServletPath("/excluded/health");

        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    @DisplayName("le premier résolveur non vide gagne")
    void firstNonEmptyResolverWins() throws Exception {
        TenantResolver empty = r -> Optional.empty();
        TenantResolver acme = r -> Optional.of("acme");
        var filter = new TenantEnforcementFilter(tenantContext, tenantService,
                List.of(empty, acme), (r, t) -> TenantAccessDecision.proceed(), List.of());
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/documents");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, chain);

        verify(tenantContext).setCurrentTenant(ACME);
    }
}
