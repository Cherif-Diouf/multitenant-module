package net.atos.digital.multitenant.tenant;

import net.atos.digital.multitenant.TenantException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantExecutor")
class TenantExecutorTest {

    @Mock
    private TenantService tenantService;

    private TenantContext tenantContext;
    private TenantExecutor executor;

    private static final Tenant ACME = new Tenant(UUID.randomUUID(), "acme", "acme");
    private static final Tenant GLOBEX = new Tenant(UUID.randomUUID(), "globex", "globex");

    @BeforeEach
    void setUp() {
        tenantContext = new TenantContext();
        executor = new TenantExecutor(tenantContext, tenantService);
        lenient().when(tenantService.getAllAvailableTenants()).thenReturn(List.of(ACME, GLOBEX));
    }

    @Test
    @DisplayName("runAs positionne le tenant pendant l'action et nettoie après")
    void runAs_setsAndClears() {
        executor.runAs(ACME, () ->
                assertThat(tenantContext.getCurrentTenant()).isEqualTo(ACME));

        assertThat(tenantContext.getCurrentTenant()).isNull();
    }

    @Test
    @DisplayName("runAs restaure le contexte précédent (appels imbriqués)")
    void runAs_restoresPreviousContext() {
        tenantContext.setCurrentTenant(ACME);
        try {
            executor.runAs(GLOBEX, () ->
                    assertThat(tenantContext.getCurrentTenant()).isEqualTo(GLOBEX));

            assertThat(tenantContext.getCurrentTenant()).isEqualTo(ACME);
        } finally {
            tenantContext.clear();
        }
    }

    @Test
    @DisplayName("runAs restaure le contexte même si l'action lève une exception")
    void runAs_restoresOnException() {
        tenantContext.setCurrentTenant(ACME);
        try {
            assertThatThrownBy(() -> executor.runAs(GLOBEX, () -> {
                throw new IllegalStateException("boom");
            })).isInstanceOf(IllegalStateException.class);

            assertThat(tenantContext.getCurrentTenant()).isEqualTo(ACME);
        } finally {
            tenantContext.clear();
        }
    }

    @Test
    @DisplayName("callAs retourne la valeur de l'action")
    void callAs_returnsValue() {
        String result = executor.callAs(ACME, () -> tenantContext.getCurrentTenantName());

        assertThat(result).isEqualTo("acme");
        assertThat(tenantContext.getCurrentTenant()).isNull();
    }

    @Test
    @DisplayName("runAs par nom résout le tenant depuis le registre (insensible à la casse)")
    void runAs_byName_resolvesFromRegistry() {
        executor.runAs("ACME", () ->
                assertThat(tenantContext.getCurrentTenant()).isEqualTo(ACME));
    }

    @Test
    @DisplayName("runAs par nom inconnu => TenantException")
    void runAs_unknownName_throws() {
        assertThatThrownBy(() -> executor.runAs("ghost", () -> {
        })).isInstanceOf(TenantException.class);
    }

    @Test
    @DisplayName("forEachTenant exécute l'action pour chaque tenant et continue sur erreur")
    void forEachTenant_continuesOnError() {
        AtomicInteger visited = new AtomicInteger();

        executor.forEachTenant(tenant -> {
            visited.incrementAndGet();
            if (tenant == ACME) {
                throw new IllegalStateException("boom on acme");
            }
        });

        assertThat(visited.get()).isEqualTo(2);
        assertThat(tenantContext.getCurrentTenant()).isNull();
    }
}
