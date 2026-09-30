package dev.aliencode.core.mission.application;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.aliencode.core.mission.application.MissionTestDoubles.FakeAgent;
import dev.aliencode.core.mission.application.MissionTestDoubles.FakeDisposeToca;
import dev.aliencode.core.mission.application.MissionTestDoubles.FakeProvisionToca;
import dev.aliencode.core.mission.application.MissionTestDoubles.InMemoryEventStore;
import dev.aliencode.core.mission.application.MissionTestDoubles.InMemoryMissions;
import dev.aliencode.core.mission.domain.model.AgentModel;
import dev.aliencode.core.mission.domain.model.EventType;
import dev.aliencode.core.mission.domain.model.Mission;
import dev.aliencode.core.mission.domain.model.MissionStatus;
import dev.aliencode.core.mission.usecase.command.StartMissionCommand;
import dev.aliencode.core.toca.domain.model.RepositorySeed;
import dev.aliencode.core.toca.domain.model.Seed;

import static dev.aliencode.core.mission.application.MissionTestDoubles.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StartMissionServiceTest {

    private final InMemoryEventStore store = new InMemoryEventStore();
    private final InMemoryMissions missions = new InMemoryMissions();
    private final MissionEventHub hub = new MissionEventHub(this.store, this.missions);
    private final FakeProvisionToca provision = new FakeProvisionToca();
    private final List<Runnable> scheduled = new ArrayList<>();

    private final MissionSettings settings = new MissionSettings(AgentModel.parse("ollama/qwen3:8b"), Duration.ofMinutes(5), false);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final MissionConductor conductor = new MissionConductor(
            this.provision,
            new FakeDisposeToca(),
            new FakeAgent(),
            this.missions,
            this.hub,
            this.settings,
            this.clock,
            this.scheduled::add
    );

    private final StartMissionService service = new StartMissionService(
            this.missions,
            this.hub,
            this.conductor,
            this.provision,
            this.settings,
            this.clock
    );

    @Test
    void registraAMissaoPublicaOCriadoEComecaAConduzir() {
        Mission mission = this.service.start(new StartMissionCommand(null, "Corrija a soma\nem calc.py", this.seed("calc"), null));

        assertThat(mission.status()).isEqualTo(MissionStatus.CREATED);
        assertThat(mission.title()).isEqualTo("Corrija a soma");
        assertThat(mission.model()).hasToString("ollama/qwen3:8b");
        assertThat(this.missions.findById(mission.id())).isPresent();
        assertThat(this.store.ofType(EventType.MISSION_CREATED)).hasSize(1);
        assertThat(this.scheduled).hasSize(1);
    }

    @Test
    void usaOModeloPedido() {
        Mission mission = this.service.start(new StartMissionCommand("Soma", "Corrija", this.seed("calc"), "ollama/devstral:24b"));

        assertThat(mission.model()).isEqualTo(new AgentModel("ollama", "devstral:24b"));
    }

    @Test
    void recusaMaisDeUmRepositorioNoM1() {
        Seed seed = new Seed.ExistingRepositories(List.of(this.repo("api"), this.repo("web")));

        assertThatThrownBy(() -> this.service.start(new StartMissionCommand(null, "Corrija", seed, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("único repositório");

        assertThat(this.missions.findAll()).isEmpty();
    }

    @Test
    void recusaNaHoraSemeaduraInvalida() {
        this.provision.invalidSeed = new IllegalArgumentException("O repositório /etc está fora das pastas permitidas");

        assertThatThrownBy(() -> this.service.start(new StartMissionCommand(null, "Corrija", this.seed("etc"), null)))
                .hasMessageContaining("fora das pastas permitidas");

        assertThat(this.scheduled).isEmpty();
    }

    @Test
    void exigePrompt() {
        assertThatThrownBy(() -> this.service.start(new StartMissionCommand(null, "  ", this.seed("calc"), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prompt");
    }

    private Seed seed(String name) {
        return new Seed.ExistingRepositories(List.of(this.repo(name)));
    }

    private RepositorySeed repo(String name) {
        return new RepositorySeed(name, Path.of("/tmp/" + name), null);
    }
}
