package dev.aliencode.adapters.mission.web.mapper;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.aliencode.adapters.mission.web.request.StartMissionRequest;
import dev.aliencode.adapters.mission.web.response.MissionResponse;
import dev.aliencode.adapters.toca.web.mapper.TocaWebMapper;
import dev.aliencode.adapters.toca.web.request.SeedRequest;
import dev.aliencode.core.mission.domain.model.AgentModel;
import dev.aliencode.core.mission.domain.model.Mission;
import dev.aliencode.core.mission.domain.model.MissionId;
import dev.aliencode.core.mission.usecase.command.StartMissionCommand;
import dev.aliencode.core.toca.domain.model.RepositorySeed;
import dev.aliencode.core.toca.domain.model.Seed;
import dev.aliencode.core.toca.domain.model.TocaId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MissionWebMapperTest {

    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

    private final MissionWebMapper mapper = new MissionWebMapper(new TocaWebMapper());

    @Test
    void projetoNovoViraComando() {
        StartMissionRequest request = new StartMissionRequest(null, "Crie uma API", null, new SeedRequest(SeedRequest.NEW, "api", null));

        StartMissionCommand command = this.mapper.toCommand(request);

        assertThat(command.seed()).isEqualTo(new Seed.NewProject("api"));
        assertThat(command.model()).isNull();
    }

    @Test
    void pedidoSemSeedEInvalido() {
        assertThatThrownBy(() -> this.mapper.toCommand(new StartMissionRequest(null, "x", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("seed.type");
    }

    @Test
    void respostaTrazTocaEMotivoDaFalha() {
        TocaId toca = TocaId.newId();
        Seed seed = new Seed.ExistingRepositories(List.of(new RepositorySeed("calc", Path.of("/home/dev/calc"), null)));
        Mission failed = Mission.create(
                MissionId.newId(),
                null,
                "Corrija",
                seed,
                AgentModel.parse("ollama/qwen3:8b"),
                NOW
        )
                .provisioning()
                .withToca(toca)
                .failed("model not found", NOW);

        MissionResponse response = this.mapper.toResponse(failed, 9L);

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.tocaId()).isEqualTo(toca.value());
        assertThat(response.failureReason()).isEqualTo("model not found");
        assertThat(response.lastSeq()).isEqualTo(9L);
    }
}
