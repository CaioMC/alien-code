package dev.aliencode.core.mission.application;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import dev.aliencode.core.mission.domain.model.AgentModel;
import dev.aliencode.core.mission.domain.model.AlienEvent;
import dev.aliencode.core.mission.domain.model.EventType;
import dev.aliencode.core.mission.domain.model.Mission;
import dev.aliencode.core.mission.domain.model.MissionId;
import dev.aliencode.core.mission.domain.model.NewEvent;
import dev.aliencode.core.mission.port.agent.AgentEvent;
import dev.aliencode.core.mission.port.agent.AgentSessionPort;
import dev.aliencode.core.mission.port.agent.AgentSubscription;
import dev.aliencode.core.mission.port.event.EventStorePort;
import dev.aliencode.core.mission.port.repository.MissionRepository;
import dev.aliencode.core.toca.domain.model.Seed;
import dev.aliencode.core.toca.domain.model.Toca;
import dev.aliencode.core.toca.domain.model.TocaEndpoint;
import dev.aliencode.core.toca.domain.model.TocaId;
import dev.aliencode.core.toca.usecase.DisposeTocaUseCase;
import dev.aliencode.core.toca.usecase.ProvisionTocaUseCase;
import dev.aliencode.core.toca.usecase.command.ProvisionTocaCommand;

import static java.util.Objects.nonNull;

/** Dublês das portas e casos de uso que a missão usa, com registro do que foi chamado. */
final class MissionTestDoubles {

    static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");
    static final TocaEndpoint ENDPOINT = new TocaEndpoint(URI.create("http://127.0.0.1:40001"), "opencode", "s3nha");
    static final String SESSION = "ses_1";
    static final String WORKSPACE = "/workspace/calc";

    private MissionTestDoubles() {
    }

    /** Agente roteirizado: ao receber o prompt, emite {@link #script}; ao receber abort, termina abortado. */
    static final class FakeAgent implements AgentSessionPort {

        final List<String> prompts = new CopyOnWriteArrayList<>();
        final List<String> aborted = new CopyOnWriteArrayList<>();
        final List<AgentEvent> script = new ArrayList<>();
        volatile boolean subscriptionClosed;

        private volatile Consumer<AgentEvent> listener;

        @Override
        public AgentSubscription subscribe(
                TocaEndpoint endpoint,
                String directory,
                Consumer<AgentEvent> listener
        ) {
            this.listener = listener;

            return () -> this.subscriptionClosed = true;
        }

        @Override
        public String createSession(
                TocaEndpoint endpoint,
                String directory,
                String title
        ) {
            return SESSION;
        }

        @Override
        public void prompt(
                TocaEndpoint endpoint,
                String directory,
                String sessionId,
                AgentModel model,
                String text
        ) {
            this.prompts.add(text);
            this.script.forEach(this::emit);
        }

        @Override
        public void abort(
                TocaEndpoint endpoint,
                String directory,
                String sessionId
        ) {
            this.aborted.add(sessionId);
            this.emit(new AgentEvent.SessionFailed(sessionId, "Aborted", true));
            this.emit(new AgentEvent.SessionIdle(sessionId));
        }

        void emit(AgentEvent event) {
            this.listener.accept(event);
        }
    }

    static class FakeProvisionToca implements ProvisionTocaUseCase {

        final List<ProvisionTocaCommand> provisioned = new CopyOnWriteArrayList<>();
        RuntimeException failure;
        RuntimeException invalidSeed;

        @Override
        public Toca provision(ProvisionTocaCommand command) {
            this.provisioned.add(command);

            if (nonNull(this.failure)) {
                throw this.failure;
            }

            return Toca.provisioning(
                    TocaId.newId(),
                    command.missionId(),
                    NOW,
                    NOW.plusSeconds(3600)
            )
                    .withContainer("c-1", ENDPOINT)
                    .ready(List.of(WORKSPACE));
        }

        @Override
        public void validate(Seed seed) {
            if (nonNull(this.invalidSeed)) {
                throw this.invalidSeed;
            }
        }
    }

    static final class FakeDisposeToca implements DisposeTocaUseCase {

        final List<TocaId> disposed = new CopyOnWriteArrayList<>();

        @Override
        public Toca dispose(TocaId id) {
            this.disposed.add(id);

            return null;
        }
    }

    static final class InMemoryMissions implements MissionRepository {

        private final Map<MissionId, Mission> missions = new ConcurrentHashMap<>();

        @Override
        public void save(Mission mission) {
            this.missions.put(mission.id(), mission);
        }

        @Override
        public Optional<Mission> findById(MissionId id) {
            return Optional.ofNullable(this.missions.get(id));
        }

        @Override
        public List<Mission> findAll() {
            return this.missions.values().stream().sorted(Comparator.comparing(Mission::createdAt).reversed()).toList();
        }
    }

    static final class InMemoryEventStore implements EventStorePort {

        final List<AlienEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public synchronized AlienEvent append(
                MissionId missionId,
                NewEvent event
        ) {
            long seq = this.findAfter(missionId, 0).size() + 1;

            AlienEvent stored = new AlienEvent(
                    missionId,
                    seq,
                    NOW,
                    event.type(),
                    event.stepId(),
                    event.parentStepId(),
                    event.source(),
                    event.payload()
            );

            this.events.add(stored);

            return stored;
        }

        @Override
        public List<AlienEvent> findAfter(
                MissionId missionId,
                long lastSeq
        ) {
            return this.events.stream().filter(e -> e.missionId().equals(missionId) && e.seq() > lastSeq).toList();
        }

        List<String> types() {
            return this.events.stream().map(e -> e.type().wireName()).toList();
        }

        List<AlienEvent> ofType(EventType type) {
            return this.events.stream().filter(e -> e.type() == type).toList();
        }

        List<Object> states() {
            return this.ofType(EventType.MISSION_STATE).stream().map(e -> e.payload().get("status")).toList();
        }
    }
}
