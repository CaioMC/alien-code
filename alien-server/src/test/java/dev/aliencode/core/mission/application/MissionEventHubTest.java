package dev.aliencode.core.mission.application;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.aliencode.core.mission.application.MissionTestDoubles.InMemoryEventStore;
import dev.aliencode.core.mission.application.MissionTestDoubles.InMemoryMissions;
import dev.aliencode.core.mission.domain.exception.MissionNotFoundException;
import dev.aliencode.core.mission.domain.model.AgentModel;
import dev.aliencode.core.mission.domain.model.AlienEvent;
import dev.aliencode.core.mission.domain.model.EventSource;
import dev.aliencode.core.mission.domain.model.EventType;
import dev.aliencode.core.mission.domain.model.Mission;
import dev.aliencode.core.mission.domain.model.MissionId;
import dev.aliencode.core.mission.domain.model.NewEvent;
import dev.aliencode.core.mission.usecase.MissionWatch;
import dev.aliencode.core.toca.domain.model.RepositorySeed;
import dev.aliencode.core.toca.domain.model.Seed;

import static dev.aliencode.core.mission.application.MissionTestDoubles.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MissionEventHubTest {

    private final InMemoryEventStore store = new InMemoryEventStore();
    private final InMemoryMissions missions = new InMemoryMissions();
    private final MissionEventHub hub = new MissionEventHub(this.store, this.missions);

    private MissionId id;

    @BeforeEach
    void createMission() {
        Seed seed = new Seed.ExistingRepositories(List.of(new RepositorySeed("calc", Path.of("/tmp/calc"), null)));
        Mission mission = Mission.create(
                MissionId.newId(),
                null,
                "Corrija a soma",
                seed,
                AgentModel.parse("ollama/qwen3:8b"),
                NOW
        );

        this.missions.save(mission);
        this.id = mission.id();
    }

    @Test
    void reconexaoRecebeSoOQuePerdeuEDepoisOAoVivo() {
        this.publish(3);

        List<Long> received = new CopyOnWriteArrayList<>();

        try (MissionWatch watch = this.hub.watch(this.id, 1, event -> received.add(event.seq()))) {
            this.publish(2);

            assertThat(received).containsExactly(2L, 3L, 4L, 5L);
        }

        this.publish(1);

        assertThat(received).containsExactly(2L, 3L, 4L, 5L);
    }

    @Test
    void conectarDuranteAPublicacaoNaoPerdeNemRepeteEventos() throws InterruptedException {
        ExecutorService publisher = Executors.newSingleThreadExecutor();

        publisher.submit(() -> this.publish(2_000));

        List<Long> received = new CopyOnWriteArrayList<>();

        try (MissionWatch watch = this.hub.watch(this.id, 0, event -> received.add(event.seq()))) {
            publisher.shutdown();

            assertThat(publisher.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            assertThat(received).containsExactlyElementsOf(LongStream.rangeClosed(1, 2_000).boxed().toList());
        }
    }

    @Test
    void ouvinteComErroNaoAtrapalhaOsOutros() {
        List<AlienEvent> received = new CopyOnWriteArrayList<>();

        this.hub.watch(this.id, 0, event -> {
            throw new IllegalStateException("WebSocket fechado");
        });
        this.hub.watch(this.id, 0, received::add);

        this.publish(1);

        assertThat(received).hasSize(1);
    }

    @Test
    void missaoInexistenteNaoPodeSerAcompanhada() {
        assertThatThrownBy(() -> this.hub.watch(MissionId.newId(), 0, event -> {
        })).isInstanceOf(MissionNotFoundException.class);
    }

    private void publish(int count) {
        for (int i = 0; i < count; i++) {
            this.hub.publish(this.id, new NewEvent(EventType.ASSISTANT_DELTA, "t1.text.p1", "t1", EventSource.OPENCODE, Map.of("text", "x")));
        }
    }
}
