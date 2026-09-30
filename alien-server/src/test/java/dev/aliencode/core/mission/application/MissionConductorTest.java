package dev.aliencode.core.mission.application;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.aliencode.core.mission.application.MissionTestDoubles.FakeAgent;
import dev.aliencode.core.mission.application.MissionTestDoubles.FakeDisposeToca;
import dev.aliencode.core.mission.application.MissionTestDoubles.FakeProvisionToca;
import dev.aliencode.core.mission.application.MissionTestDoubles.InMemoryEventStore;
import dev.aliencode.core.mission.application.MissionTestDoubles.InMemoryMissions;
import dev.aliencode.core.mission.domain.model.AgentModel;
import dev.aliencode.core.mission.domain.model.AlienEvent;
import dev.aliencode.core.mission.domain.model.EventType;
import dev.aliencode.core.mission.domain.model.Mission;
import dev.aliencode.core.mission.domain.model.MissionId;
import dev.aliencode.core.mission.domain.model.MissionStatus;
import dev.aliencode.core.mission.port.agent.AgentEvent;
import dev.aliencode.core.toca.domain.model.RepositorySeed;
import dev.aliencode.core.toca.domain.model.Seed;
import dev.aliencode.core.toca.domain.model.Toca;
import dev.aliencode.core.toca.usecase.command.ProvisionTocaCommand;

import static dev.aliencode.core.mission.application.MissionTestDoubles.NOW;
import static dev.aliencode.core.mission.application.MissionTestDoubles.SESSION;
import static dev.aliencode.core.mission.application.MissionTestDoubles.WORKSPACE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class MissionConductorTest {

    private static final AgentModel MODEL = AgentModel.parse("ollama/qwen3:8b");

    private final InMemoryEventStore store = new InMemoryEventStore();
    private final InMemoryMissions missions = new InMemoryMissions();
    private final MissionEventHub hub = new MissionEventHub(this.store, this.missions);

    private final FakeAgent agent = new FakeAgent();
    private final FakeProvisionToca provision = new FakeProvisionToca();
    private final FakeDisposeToca dispose = new FakeDisposeToca();

    private final ExecutorService background = Executors.newSingleThreadExecutor();

    @AfterEach
    void stopBackground() {
        this.background.shutdownNow();
    }

    @Test
    void conduzATarefaAteConcluirEDescartaAToca() {
        this.agent.script.addAll(List.of(
                new AgentEvent.ReasoningDelta(SESSION, "p1", "Preciso ler "),
                new AgentEvent.TextDelta(SESSION, "p2", "Vou ler o calc.py"),
                new AgentEvent.ToolStarted(SESSION, "call_0", "read", WORKSPACE + "/calc.py", Map.of("filePath", WORKSPACE + "/calc.py")),
                new AgentEvent.ToolFinished(SESSION, "call_0", "read", true, "calc.py", "def soma", null, 24L),
                new AgentEvent.FileChanged(SESSION, WORKSPACE + "/calc.py", "-a - b\n+a + b", 1, 1),
                new AgentEvent.ToolStarted(SESSION, "call_1", "bash", "python3 teste.py", Map.of("command", "python3 teste.py")),
                new AgentEvent.ToolFinished(SESSION, "call_1", "bash", true, "python3 teste.py", "5\n", 0, 146L),
                new AgentEvent.ModelCallFinished(SESSION, 6724, 42, 0),
                new AgentEvent.SessionIdle(SESSION)
        ));

        Mission mission = this.conduct(this.conductor(Runnable::run, Duration.ofSeconds(5), false));

        assertThat(this.missions.findById(mission.id()).orElseThrow().status()).isEqualTo(MissionStatus.COMPLETED);
        assertThat(this.store.states()).containsExactly("PROVISIONING", "PROVISIONING", "EXECUTING", "COMPLETED");
        assertThat(this.agent.prompts).containsExactly("Corrija a soma");
        assertThat(this.agent.subscriptionClosed).isTrue();
        assertThat(this.dispose.disposed).hasSize(1);

        assertThat(this.store.types()).containsSubsequence(
                "step.started",
                "step.completed",
                "step.started",
                "thinking.delta",
                "assistant.delta",
                "tool.started",
                "tool.completed",
                "file.changed",
                "tool.started",
                "tool.completed",
                "terminal.output",
                "budget.updated",
                "step.completed",
                "mission.state",
                "step.completed"
        );

        AlienEvent terminal = this.store.ofType(EventType.TERMINAL_OUTPUT).getFirst();

        assertThat(terminal.stepId()).isEqualTo("t1.tool.call_1");
        assertThat(terminal.parentStepId()).isEqualTo("t1");
        assertThat(terminal.payload()).containsEntry("output", "5\n").containsEntry("exitCode", 0);

        assertThat(this.store.ofType(EventType.BUDGET_UPDATED).getFirst().payload()).containsEntry("totalTokens", 6766L);
        assertThat(this.store.ofType(EventType.STEP_COMPLETED).getLast().stepId()).isEqualTo(MissionConductor.DISPOSE_STEP);
    }

    @Test
    void semeiaATocaComOIdDaMissao() {
        this.agent.script.add(new AgentEvent.SessionIdle(SESSION));

        Mission mission = this.conduct(this.conductor(Runnable::run, Duration.ofSeconds(5), false));

        ProvisionTocaCommand command = this.provision.provisioned.getFirst();

        assertThat(command.missionId()).isEqualTo(mission.id().value());
        assertThat(command.seed()).isEqualTo(mission.seed());
    }

    @Test
    void ignoraEventosDeOutraSessao() {
        this.agent.script.addAll(List.of(
                new AgentEvent.TextDelta("ses_outra", "p9", "não é desta missão"),
                new AgentEvent.SessionIdle("ses_outra"),
                new AgentEvent.SessionIdle(SESSION)
        ));

        this.conduct(this.conductor(Runnable::run, Duration.ofSeconds(5), false));

        assertThat(this.store.ofType(EventType.ASSISTANT_DELTA)).isEmpty();
    }

    @Test
    void erroDoProvedorFalhaAMissaoComOMotivo() {
        this.agent.script.addAll(List.of(
                new AgentEvent.SessionFailed(SESSION, "model not found: qwen9:999b", false),
                new AgentEvent.SessionIdle(SESSION)
        ));

        Mission mission = this.conduct(this.conductor(Runnable::run, Duration.ofSeconds(5), false));
        Mission failed = this.missions.findById(mission.id()).orElseThrow();

        assertThat(failed.status()).isEqualTo(MissionStatus.FAILED);
        assertThat(failed.failureReason()).isEqualTo("model not found: qwen9:999b");
        assertThat(this.store.ofType(EventType.STEP_FAILED).getFirst().stepId()).isEqualTo(MissionConductor.TASK_STEP);
        assertThat(this.dispose.disposed).hasSize(1);
    }

    @Test
    void pararDuranteAExecucaoAbortaASessaoEFechaAsToolsAbertas() {
        this.agent.script.add(new AgentEvent.ToolStarted(SESSION, "call_0", "bash", "sleep 60", Map.of("command", "sleep 60")));

        MissionConductor conductor = this.conductor(this.background, Duration.ofSeconds(30), false);
        Mission mission = this.conduct(conductor);

        await().atMost(5, TimeUnit.SECONDS).until(() -> this.status(mission.id()) == MissionStatus.EXECUTING && !this.agent.prompts.isEmpty());

        assertThat(conductor.requestStop(mission.id())).isTrue();

        await().atMost(5, TimeUnit.SECONDS).until(() -> this.status(mission.id()) == MissionStatus.CANCELLED);

        assertThat(this.agent.aborted).containsExactly(SESSION);
        assertThat(this.store.ofType(EventType.TOOL_COMPLETED).getFirst().payload()).containsEntry("status", "cancelled");
        await().atMost(5, TimeUnit.SECONDS).until(() -> this.dispose.disposed.size() == 1);
    }

    @Test
    void pararDuranteOProvisionamentoNaoChegaAoAgente() throws InterruptedException {
        CountDownLatch provisioning = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        FakeProvisionToca slow = new FakeProvisionToca() {
            @Override
            public Toca provision(ProvisionTocaCommand command) {
                provisioning.countDown();
                awaitQuietly(release);

                return super.provision(command);
            }
        };

        MissionConductor conductor = new MissionConductor(
                slow,
                this.dispose,
                this.agent,
                this.missions,
                this.hub,
                new MissionSettings(MODEL, Duration.ofSeconds(30), false),
                Clock.fixed(NOW, ZoneOffset.UTC),
                this.background
        );

        Mission mission = this.conduct(conductor);

        assertThat(provisioning.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(conductor.requestStop(mission.id())).isTrue();

        release.countDown();

        await().atMost(5, TimeUnit.SECONDS).until(() -> this.status(mission.id()) == MissionStatus.CANCELLED);

        assertThat(this.agent.prompts).isEmpty();
        await().atMost(5, TimeUnit.SECONDS).until(() -> this.dispose.disposed.size() == 1);
    }

    @Test
    void falhaNoProvisionamentoFalhaAMissaoSemDescarte() {
        this.provision.failure = new IllegalStateException("Imagem da Toca não encontrada");

        Mission mission = this.conduct(this.conductor(Runnable::run, Duration.ofSeconds(5), false));

        assertThat(this.status(mission.id())).isEqualTo(MissionStatus.FAILED);
        assertThat(this.store.ofType(EventType.STEP_FAILED).getFirst().payload()).containsEntry("error", "Imagem da Toca não encontrada");
        assertThat(this.agent.prompts).isEmpty();
        assertThat(this.dispose.disposed).isEmpty();
    }

    @Test
    void tempoEsgotadoAbortaEFalhaAMissao() {
        Mission mission = this.conduct(this.conductor(Runnable::run, Duration.ofMillis(200), false));
        Mission failed = this.missions.findById(mission.id()).orElseThrow();

        assertThat(failed.status()).isEqualTo(MissionStatus.FAILED);
        assertThat(failed.failureReason()).contains("tempo máximo");
        assertThat(this.agent.aborted).containsExactly(SESSION);
    }

    @Test
    void modoManterTocaNaoDescarta() {
        this.agent.script.add(new AgentEvent.SessionIdle(SESSION));

        this.conduct(this.conductor(Runnable::run, Duration.ofSeconds(5), true));

        assertThat(this.dispose.disposed).isEmpty();
    }

    private MissionConductor conductor(
            Executor executor,
            Duration taskTimeout,
            boolean keepToca
    ) {
        return new MissionConductor(
                this.provision,
                this.dispose,
                this.agent,
                this.missions,
                this.hub,
                new MissionSettings(MODEL, taskTimeout, keepToca),
                Clock.fixed(NOW, ZoneOffset.UTC),
                executor
        );
    }

    private Mission conduct(MissionConductor conductor) {
        Seed seed = new Seed.ExistingRepositories(List.of(new RepositorySeed("calc", Path.of("/tmp/calc"), null)));
        Mission mission = Mission.create(
                MissionId.newId(),
                null,
                "Corrija a soma",
                seed,
                MODEL,
                NOW
        );

        this.missions.save(mission);
        conductor.conduct(mission);

        return mission;
    }

    private MissionStatus status(MissionId id) {
        return this.missions.findById(id).orElseThrow().status();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
