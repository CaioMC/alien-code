package dev.aliencode.core.toca.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class TocaTest {

    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");
    private static final TocaEndpoint ENDPOINT = new TocaEndpoint(URI.create("http://127.0.0.1:1234"), "opencode", "s3nha");

    @Test
    void segueOCicloProvisionandoProntaDescartada() {
        Toca toca = Toca.provisioning(TocaId.newId(), "m-1", NOW, NOW.plusSeconds(60));

        Toca ready = toca.withContainer("abc", ENDPOINT).ready(List.of("/workspace/api"));
        assertThat(ready.status()).isEqualTo(TocaStatus.READY);
        assertThat(ready.workspaceDirs()).containsExactly("/workspace/api");

        assertThat(ready.disposed().status()).isEqualTo(TocaStatus.DISPOSED);
    }

    @Test
    void naoFicaProntaSemContainer() {
        Toca toca = Toca.provisioning(TocaId.newId(), null, NOW, NOW.plusSeconds(60));

        assertThatThrownBy(() -> toca.ready(List.of())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void expiraSoEnquantoAtiva() {
        Toca toca = Toca.provisioning(TocaId.newId(), null, NOW, NOW.plusSeconds(60));

        assertThat(toca.isExpired(NOW.plusSeconds(59))).isFalse();
        assertThat(toca.isExpired(NOW.plusSeconds(60))).isTrue();
        assertThat(toca.disposed().isExpired(NOW.plusSeconds(120))).isFalse();
    }

    @Test
    void idTemFormatoDeNomeDeContainer() {
        assertThat(TocaId.newId().value()).matches("toca-[a-f0-9]{8}");
        assertThatThrownBy(() -> new TocaId("../etc")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void endpointNaoExpoeASenhaNoToString() {
        assertThat(ENDPOINT.toString()).doesNotContain("s3nha");
    }

    @Test
    void seedRecusaNomesInvalidosERepetidos() {
        assertThatThrownBy(() -> new RepositorySeed("../fora", Path.of("/tmp/x"), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Seed.ExistingRepositories(List.of(
                new RepositorySeed("api", Path.of("/tmp/a"), null),
                new RepositorySeed("api", Path.of("/tmp/b"), null))))
                .hasMessageContaining("repetido");
        assertThatThrownBy(() -> new Seed.ExistingRepositories(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
