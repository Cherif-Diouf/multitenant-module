package net.atos.digital.multitenant.resolution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HeaderTenantResolver")
class HeaderTenantResolverTest {

    private final HeaderTenantResolver resolver = new HeaderTenantResolver();

    @Test
    @DisplayName("header présent => nom normalisé en minuscules")
    void header_isNormalizedLowercase() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-TENANT", "  ACME ");

        assertThat(resolver.resolveTenantName(req)).contains("acme");
    }

    @Test
    @DisplayName("header absent ou vide => empty")
    void missingHeader_returnsEmpty() {
        assertThat(resolver.resolveTenantName(new MockHttpServletRequest())).isEmpty();

        MockHttpServletRequest blank = new MockHttpServletRequest();
        blank.addHeader("X-TENANT", "   ");
        assertThat(resolver.resolveTenantName(blank)).isEmpty();
    }

    @Test
    @DisplayName("prioritaire sur les résolveurs applicatifs (ordre négatif)")
    void order_isBeforeApplicationResolvers() {
        assertThat(resolver.getOrder()).isLessThan(0);
    }
}
