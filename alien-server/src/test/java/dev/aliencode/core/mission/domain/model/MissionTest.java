package dev.aliencode.core.mission.domain.model;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.aliencode.core.toca.domain.model.RepositorySeed;
import dev.aliencode.core.toca.domain.model.Seed;
import dev.aliencode.core.toca.domain.model.TocaId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MissionTest {

    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");
    private static final Seed SEED = new Seed.ExistingRepositories(List.of(new RepositorySeed("calc", Path.of("/tmp/calc"), null)));
    private static final AgentModel MODEL = AgentModel.parse("ollama/qwen3:8b");

    @Test
    void segueOCicloCriadaProvisionandoExecutandoConcluida() {
        TocaId toca = TocaId.newId();
        Mission mission = Mission.create(
                MissionId.newId(),
                "Soma",
                "Corrija a soma",
                SEED,
                MODEL,
                NOW
        );

        Mission executing = mission.provisioning().withToca(toca).executing(toca, "ses_1");

        assertThat(executing.status()).isEqualTo(MissionStatus.EXECUTING);
        assertThat(executing.sessionId()).isEqualTo("ses_1");

        Mission completed = executing.completed(NOW.plusSeconds(90));

        assertThat(completed.status()).isEqualTo(MissionStatus.COMPLETED);
        assertThat(completed.finishedAt()).isEqualTo(NOW.plusSeconds(90));
        assertThat(completed.status().isActive()).isFalse();
    }

    @Test
    void naoExecutaSemProvisionar() {
        Mission mission = Mission.create(
                MissionId.newId(),
                "Soma",
                "Corrija",
                SEED,
                MODEL,
                NOW
        );

        assertThatThrownBy(() -> mission.executing(TocaId.newId(), "ses_1")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missaoTerminadaNaoMudaMais() {
        Mission cancelled = Mission.create(
                MissionId.newId(),
                "Soma",
                "Corrija",
                SEED,
                MODEL,
                NOW
        ).cancelled(NOW);

        assertThatThrownBy(() -> cancelled.failed("tarde demais", NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void semTituloUsaAPrimeiraLinhaDoPrompt() {
        Mission mission = Mission.create(
                MissionId.newId(),
                " ",
                "  Corrija a soma\nDetalhes...",
                SEED,
                MODEL,
                NOW
        );

        assertThat(mission.title()).isEqualTo("Corrija a soma");
    }

    @Test
    void modeloPrecisaDeProvedorEModelo() {
        assertThat(AgentModel.parse("ollama/qwen3:8b")).isEqualTo(new AgentModel("ollama", "qwen3:8b"));
        assertThat(AgentModel.parse("ollama/library/x:1").modelId()).isEqualTo("library/x:1");

        assertThatThrownBy(() -> AgentModel.parse("qwen3:8b")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void idTemFormatoRestrito() {
        assertThat(MissionId.newId().value()).matches("m-[a-f0-9]{8}");

        assertThatThrownBy(() -> new MissionId("../etc")).isInstanceOf(IllegalArgumentException.class);
    }
}
