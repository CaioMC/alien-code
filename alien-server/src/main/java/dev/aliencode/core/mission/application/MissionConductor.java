package dev.aliencode.core.mission.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import dev.aliencode.core.mission.domain.model.Delivery;
import dev.aliencode.core.mission.domain.model.EventSource;
import dev.aliencode.core.mission.domain.model.EventType;
import dev.aliencode.core.mission.domain.model.Mission;
import dev.aliencode.core.mission.domain.model.MissionId;
import dev.aliencode.core.mission.domain.model.NewEvent;
import dev.aliencode.core.mission.port.agent.AgentEvent;
import dev.aliencode.core.mission.port.agent.AgentSessionPort;
import dev.aliencode.core.mission.port.agent.AgentSubscription;
import dev.aliencode.core.mission.port.repository.DeliveryRepository;
import dev.aliencode.core.toca.domain.model.RepositorySeed;
import dev.aliencode.core.toca.domain.model.Seed;
import dev.aliencode.core.toca.domain.model.Toca;
import dev.aliencode.core.toca.domain.model.TocaEndpoint;
import dev.aliencode.core.toca.domain.model.WorkspaceChanges;
import dev.aliencode.core.toca.usecase.DisposeTocaUseCase;
import dev.aliencode.core.toca.usecase.HarvestTocaUseCase;
import dev.aliencode.core.toca.usecase.ProvisionTocaUseCase;
import dev.aliencode.core.toca.usecase.command.ProvisionTocaCommand;

import static java.util.Objects.isNull;

/**
 * Orquestrador de missões: provisiona a Toca, abre uma sessão do agente com a tarefa,
 * transforma o que o agente faz em eventos da timeline, colhe a entrega e decide o fim da missão.
 * O modelo nunca decide transições de estado; quem decide é este orquestrador.
 *
 * <p>Cada missão roda num thread próprio do {@code missionExecutor}. A parada chega por
 * {@link #requestStop(MissionId)}, de outro thread.
 */
@Service
public class MissionConductor {

    private static final Logger log = LoggerFactory.getLogger(MissionConductor.class);

    static final String TOCA_STEP = "toca";
    static final String TASK_STEP = "t1";
    static final String HARVEST_STEP = "toca.harvest";
    static final String DISPOSE_STEP = "toca.dispose";

    private final ProvisionTocaUseCase provisionToca;
    private final HarvestTocaUseCase harvestToca;
    private final DisposeTocaUseCase disposeToca;
    private final AgentSessionPort agent;
    private final DeliveryRepository deliveries;
    private final MissionTransitions transitions;
    private final MissionEventHub events;
    private final MissionSettings settings;
    private final Clock clock;

    private final Executor executor;
    private final Map<MissionId, RunningMission> running = new ConcurrentHashMap<>();

    public MissionConductor(
            ProvisionTocaUseCase provisionToca,
            HarvestTocaUseCase harvestToca,
            DisposeTocaUseCase disposeToca,
            AgentSessionPort agent,
            DeliveryRepository deliveries,
            MissionTransitions transitions,
            MissionEventHub events,
            MissionSettings settings,
            Clock clock,
            @Qualifier("missionExecutor") Executor executor
    ) {
        this.provisionToca = provisionToca;
        this.harvestToca = harvestToca;
        this.disposeToca = disposeToca;
        this.agent = agent;
        this.deliveries = deliveries;
        this.transitions = transitions;
        this.events = events;
        this.settings = settings;
        this.clock = clock;
        this.executor = executor;
    }

    /** Começa a conduzir a missão em segundo plano. */
    public void conduct(Mission mission) {
        RunningMission run = new RunningMission(mission.id());

        this.running.put(mission.id(), run);
        this.executor.execute(() -> this.run(mission, run));
    }

    /** @return {@code true} se a missão estava sendo conduzida e a parada foi pedida */
    public boolean requestStop(MissionId id) {
        RunningMission run = this.running.get(id);

        if (isNull(run)) {
            return false;
        }

        if (run.requestStop()) {
            this.abortQuietly(run);
        }

        return true;
    }

    boolean isRunning(MissionId id) {
        return this.running.containsKey(id);
    }

    private void run(
            Mission mission,
            RunningMission run
    ) {
        Mission current = mission;

        try {
            current = this.transition(current.provisioning());

            Optional<Toca> toca = this.provision(current);

            if (toca.isEmpty()) {
                current = this.transition(current.failed("Não foi possível provisionar a Toca", this.clock.instant()));
                return;
            }

            current = this.transition(current.withToca(toca.get().id()));

            if (run.isStopRequested()) {
                current = this.transition(current.cancelled(this.clock.instant()));
                return;
            }

            current = this.execute(current, toca.get(), run);
        } catch (RuntimeException e) {
            log.warn("Falha conduzindo a missão {}: {}", mission.id(), e.getMessage(), e);

            if (current.status().isActive()) {
                current = this.transition(current.failed(e.getMessage(), this.clock.instant()));
            }
        } finally {
            this.running.remove(mission.id());
            this.disposeIfNeeded(current);
        }
    }

    private Optional<Toca> provision(Mission mission) {
        Instant start = this.clock.instant();

        this.events.publish(mission.id(), this.docker(EventType.STEP_STARTED, TOCA_STEP, Map.of("title", "Provisionar Toca")));

        try {
            Toca toca = this.provisionToca.provision(new ProvisionTocaCommand(mission.id().value(), mission.seed()));

            Map<String, Object> payload = new LinkedHashMap<>();

            payload.put("title", "Provisionar Toca");
            payload.put("tocaId", toca.id().value());
            payload.put("workspace", toca.workspaceDirs());
            payload.put("durationMs", this.elapsedMs(start));

            this.events.publish(mission.id(), this.docker(EventType.STEP_COMPLETED, TOCA_STEP, payload));

            return Optional.of(toca);
        } catch (RuntimeException e) {
            Map<String, Object> payload = new LinkedHashMap<>();

            payload.put("title", "Provisionar Toca");
            payload.put("error", e.getMessage());
            payload.put("durationMs", this.elapsedMs(start));

            this.events.publish(mission.id(), this.docker(EventType.STEP_FAILED, TOCA_STEP, payload));
            log.warn("Missão {}: Toca não provisionada: {}", mission.id(), e.getMessage());

            return Optional.empty();
        }
    }

    private Mission execute(
            Mission mission,
            Toca toca,
            RunningMission run
    ) {
        TocaEndpoint endpoint = toca.endpoint();
        String directory = toca.workspaceDirs().getFirst();
        TaskTimeline timeline = new TaskTimeline(TASK_STEP);
        Instant start = this.clock.instant();

        try (AgentSubscription subscription = this.agent.subscribe(endpoint, directory, event -> this.onAgentEvent(run, timeline, event))) {
            String sessionId = this.agent.createSession(endpoint, directory, mission.title());

            Mission executing = this.transition(mission.executing(toca.id(), sessionId));

            this.events.publish(mission.id(), this.taskStarted(executing));

            if (run.attachSession(endpoint, directory, sessionId)) {
                this.abortQuietly(run);
            } else {
                this.agent.prompt(
                        endpoint,
                        directory,
                        sessionId,
                        executing.model(),
                        executing.prompt()
                );
            }

            TaskOutcome outcome = run.await(this.settings.taskTimeout());

            if (outcome.kind() == TaskOutcome.Kind.TIMED_OUT) {
                this.abortQuietly(run);
            }

            return this.finish(
                    executing,
                    toca,
                    timeline,
                    outcome,
                    start
            );
        }
    }

    private void onAgentEvent(
            RunningMission run,
            TaskTimeline timeline,
            AgentEvent event
    ) {
        if (!event.sessionId().equals(run.sessionId())) {
            return;
        }

        this.events.publishAll(run.id(), timeline.translate(event));

        switch (event) {
            case AgentEvent.SessionFailed failed when failed.aborted() -> run.finish(TaskOutcome.stopped());
            case AgentEvent.SessionFailed failed -> run.finish(TaskOutcome.failed(failed.message()));
            case AgentEvent.SessionIdle idle -> run.finish(TaskOutcome.completed());
            default -> {
            }
        }
    }

    private Mission finish(
            Mission mission,
            Toca toca,
            TaskTimeline timeline,
            TaskOutcome outcome,
            Instant start
    ) {
        Instant now = this.clock.instant();

        this.events.publishAll(mission.id(), timeline.closeOpenTools(outcome.kind() == TaskOutcome.Kind.COMPLETED ? "Sessão encerrada" : outcome.message()));

        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put("title", mission.title());
        payload.put("durationMs", Duration.between(start, now).toMillis());
        payload.put("reason", outcome.message());

        EventType type = outcome.kind() == TaskOutcome.Kind.COMPLETED ? EventType.STEP_COMPLETED : EventType.STEP_FAILED;

        this.events.publish(mission.id(), this.alien(type, TASK_STEP, payload));

        return switch (outcome.kind()) {
            case COMPLETED -> this.harvest(mission, toca);
            case STOPPED -> this.transition(mission.cancelled(now));
            case FAILED, TIMED_OUT -> this.transition(mission.failed(outcome.message(), now));
        };
    }

    /**
     * Passo 6 (Colher): o patch sai da Toca antes de ela ser descartada e fica esperando o dev.
     * Projeto novo ainda não tem para onde ser entregue: termina como antes.
     */
    private Mission harvest(
            Mission mission,
            Toca toca
    ) {
        if (!(mission.seed() instanceof Seed.ExistingRepositories(List<RepositorySeed> repositories))) {
            return this.transition(mission.completed(this.clock.instant()));
        }

        RepositorySeed repository = repositories.getFirst();
        Instant start = this.clock.instant();

        this.events.publish(mission.id(), this.docker(EventType.STEP_STARTED, HARVEST_STEP, Map.of("title", "Colher entrega")));

        try {
            WorkspaceChanges changes = this.harvestToca.harvest(
                    toca.id(),
                    toca.workspaceDirs().getFirst(),
                    mission.title()
            );

            Map<String, Object> payload = new LinkedHashMap<>();

            payload.put("title", changes.isEmpty() ? "Colher entrega: nenhuma alteração" : "Colher entrega");
            payload.put("files", changes.files().size());
            payload.put("durationMs", this.elapsedMs(start));

            this.events.publish(mission.id(), this.docker(EventType.STEP_COMPLETED, HARVEST_STEP, payload));

            if (changes.isEmpty()) {
                return this.transition(mission.completed(this.clock.instant()));
            }

            Delivery delivery = Delivery.pending(
                    mission.id(),
                    repository.name(),
                    repository.path(),
                    changes,
                    this.clock.instant()
            );

            this.deliveries.save(delivery);
            this.events.publish(mission.id(), DeliveryEvents.ready(delivery));

            return this.transition(mission.awaitingReview());
        } catch (RuntimeException e) {
            Map<String, Object> payload = new LinkedHashMap<>();

            payload.put("title", "Colher entrega");
            payload.put("error", e.getMessage());
            payload.put("durationMs", this.elapsedMs(start));

            this.events.publish(mission.id(), this.docker(EventType.STEP_FAILED, HARVEST_STEP, payload));
            log.warn("Missão {}: entrega não colhida: {}", mission.id(), e.getMessage(), e);

            return this.transition(mission.failed("Não foi possível colher a entrega: " + e.getMessage(), this.clock.instant()));
        }
    }

    private void disposeIfNeeded(Mission mission) {
        if (!mission.hasToca() || this.settings.keepToca()) {
            return;
        }

        Instant start = this.clock.instant();

        try {
            this.disposeToca.dispose(mission.tocaId());

            Map<String, Object> payload = new LinkedHashMap<>();

            payload.put("title", "Descartar Toca");
            payload.put("tocaId", mission.tocaId().value());
            payload.put("durationMs", this.elapsedMs(start));

            this.events.publish(mission.id(), this.docker(EventType.STEP_COMPLETED, DISPOSE_STEP, payload));
        } catch (RuntimeException e) {
            log.warn("Missão {}: não foi possível descartar a {} (o faxineiro tenta de novo): {}", mission.id(), mission.tocaId(), e.getMessage());
        }
    }

    private Mission transition(Mission mission) {
        return this.transitions.record(mission);
    }

    private NewEvent taskStarted(Mission mission) {
        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put("title", mission.title());
        payload.put("prompt", mission.prompt());
        payload.put("model", mission.model().toString());
        payload.put("sessionId", mission.sessionId());

        return this.alien(EventType.STEP_STARTED, TASK_STEP, payload);
    }

    private NewEvent docker(
            EventType type,
            String stepId,
            Map<String, Object> payload
    ) {
        return new NewEvent(
                type,
                stepId,
                null,
                EventSource.DOCKER,
                payload
        );
    }

    private NewEvent alien(
            EventType type,
            String stepId,
            Map<String, Object> payload
    ) {
        return new NewEvent(
                type,
                stepId,
                null,
                EventSource.ALIEN,
                payload
        );
    }

    private long elapsedMs(Instant start) {
        return Duration.between(start, this.clock.instant()).toMillis();
    }

    private void abortQuietly(RunningMission run) {
        try {
            this.agent.abort(run.endpoint(), run.directory(), run.sessionId());
        } catch (RuntimeException e) {
            log.warn("Missão {}: abort da sessão {} falhou: {}", run.id(), run.sessionId(), e.getMessage());
        }
    }
}
