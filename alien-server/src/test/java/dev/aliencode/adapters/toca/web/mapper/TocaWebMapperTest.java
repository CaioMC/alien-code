package dev.aliencode.adapters.toca.web.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.aliencode.adapters.toca.web.request.ProvisionTocaRequest;
import dev.aliencode.adapters.toca.web.request.RepositoryRequest;
import dev.aliencode.adapters.toca.web.request.SeedRequest;
import dev.aliencode.adapters.toca.web.response.TocaResponse;
import dev.aliencode.core.toca.domain.model.RepositorySeed;
import dev.aliencode.core.toca.domain.model.Seed;
import dev.aliencode.core.toca.domain.model.Toca;
import dev.aliencode.core.toca.domain.model.TocaEndpoint;
import dev.aliencode.core.toca.domain.model.TocaId;
import dev.aliencode.core.toca.usecase.command.ProvisionTocaCommand;

class TocaWebMapperTest {

    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");

    private final TocaWebMapper mapper = new TocaWebMapper();

    @Test
    void repositoriosExistentesViramSeedDoDominio() {
        ProvisionTocaRequest request = new ProvisionTocaRequest("m-1", new SeedRequest(SeedRequest.EXISTING, null, List.of(
                new RepositoryRequest(null, "/home/dev/minha-api", "main"),
                new RepositoryRequest("web", "/home/dev/front", null))));

        ProvisionTocaCommand command = this.mapper.toCommand(request);

        assertThat(command.missionId()).isEqualTo("m-1");
        assertThat(((Seed.ExistingRepositories) command.seed()).repositories()).containsExactly(
                new RepositorySeed("minha-api", Path.of("/home/dev/minha-api"), "main"),
                new RepositorySeed("web", Path.of("/home/dev/front"), null));
    }

    @Test
    void projetoNovoViraNewProject() {
        ProvisionTocaCommand command = this.mapper.toCommand(
                new ProvisionTocaRequest(null, new SeedRequest(SeedRequest.NEW, "demo", null)));

        assertThat(command.seed()).isEqualTo(new Seed.NewProject("demo"));
    }

    @Test
    void recusaSeedAusenteTipoDesconhecidoERepositorioSemPath() {
        assertThatThrownBy(() -> this.mapper.toCommand(new ProvisionTocaRequest(null, null)))
                .hasMessageContaining("seed.type");
        assertThatThrownBy(() -> this.mapper.toCommand(new ProvisionTocaRequest(null, new SeedRequest("nuvem", null, null))))
                .hasMessageContaining("nuvem");
        assertThatThrownBy(() -> this.mapper.toCommand(new ProvisionTocaRequest(null,
                new SeedRequest(SeedRequest.EXISTING, null, List.of(new RepositoryRequest("api", " ", null))))))
                .hasMessageContaining("precisa de path");
    }

    @Test
    void respostaEncurtaOContainerENaoLevaASenha() {
        Toca toca = Toca.provisioning(TocaId.newId(), "m-1", NOW, NOW.plusSeconds(3600))
                .withContainer("0123456789abcdef", new TocaEndpoint(URI.create("http://127.0.0.1:40001"), "opencode", "s3nha"))
                .ready(List.of("/workspace/api"));

        TocaResponse response = this.mapper.toResponse(toca);

        assertThat(response.containerId()).isEqualTo("0123456789ab");
        assertThat(response.agentUrl()).isEqualTo("http://127.0.0.1:40001");
        assertThat(response.status()).isEqualTo("READY");
        assertThat(response.toString()).doesNotContain("s3nha");
    }
}
