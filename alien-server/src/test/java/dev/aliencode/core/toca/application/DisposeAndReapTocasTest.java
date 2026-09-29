package dev.aliencode.core.toca.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.aliencode.core.toca.application.TocaTestDoubles.FakeSandbox;
import dev.aliencode.core.toca.application.TocaTestDoubles.InMemoryTocas;
import dev.aliencode.core.toca.domain.exception.TocaNotFoundException;
import dev.aliencode.core.toca.domain.model.Toca;
import dev.aliencode.core.toca.domain.model.TocaEndpoint;
import dev.aliencode.core.toca.domain.model.TocaId;
import dev.aliencode.core.toca.domain.model.TocaStatus;
import dev.aliencode.core.toca.port.sandbox.ManagedSandbox;

class DisposeAndReapTocasTest {

    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");

    private FakeSandbox sandbox;
    private InMemoryTocas tocas;
    private DisposeTocaService dispose;
    private ReapTocasService reap;

    @BeforeEach
    void setUp() {
        this.sandbox = new FakeSandbox();
        this.tocas = new InMemoryTocas();
        this.dispose = new DisposeTocaService(this.sandbox, this.tocas);
        this.reap = new ReapTocasService(this.tocas, this.sandbox, this.dispose, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void descartarRemoveOContainerEEIdempotente() {
        Toca toca = this.readyToca(NOW.plusSeconds(600));

        assertThat(this.dispose.dispose(toca.id()).status()).isEqualTo(TocaStatus.DISPOSED);
        assertThat(this.dispose.dispose(toca.id()).status()).isEqualTo(TocaStatus.DISPOSED);
        assertThat(this.sandbox.removed).containsExactly(toca.containerId());
    }

    @Test
    void descartarTocaDesconhecidaFalha() {
        assertThatThrownBy(() -> this.dispose.dispose(TocaId.newId())).isInstanceOf(TocaNotFoundException.class);
    }

    @Test
    void faxinaDescartaSoAsVencidas() {
        Toca vencida = this.readyToca(NOW.minusSeconds(1));
        Toca valida = this.readyToca(NOW.plusSeconds(600));

        assertThat(this.reap.reapExpired()).containsExactly(vencida.id().value());
        assertThat(this.tocas.findById(valida.id()).orElseThrow().status()).isEqualTo(TocaStatus.READY);
    }

    @Test
    void removeContainersOrfaosMasPreservaTocasAtivas() {
        Toca ativa = this.readyToca(NOW.plusSeconds(600));
        this.sandbox.managed.add(new ManagedSandbox(ativa.containerId(), ativa.id().value()));
        this.sandbox.managed.add(new ManagedSandbox("orfao-1", "toca-0000dead"));

        assertThat(this.reap.removeOrphans()).containsExactly("orfao-1");
        assertThat(this.sandbox.removed).containsExactly("orfao-1");
    }

    private Toca readyToca(Instant expiresAt) {
        TocaId id = TocaId.newId();
        Toca toca = Toca.provisioning(id, null, NOW.minusSeconds(3600), expiresAt)
                .withContainer("c-" + id.value(), new TocaEndpoint(URI.create("http://127.0.0.1:1"), "opencode", "x"))
                .ready(List.of());
        this.tocas.save(toca);
        return toca;
    }
}
