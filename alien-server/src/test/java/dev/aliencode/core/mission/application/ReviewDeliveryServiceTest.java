package dev.aliencode.core.mission.application;

import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.aliencode.core.mission.application.MissionTestDoubles.FakeDeliveryTarget;
import dev.aliencode.core.mission.application.MissionTestDoubles.InMemoryDeliveries;
import dev.aliencode.core.mission.application.MissionTestDoubles.InMemoryEventStore;
import dev.aliencode.core.mission.application.MissionTestDoubles.InMemoryMissions;
import dev.aliencode.core.mission.domain.exception.DeliveryConflictException;
import dev.aliencode.core.mission.domain.model.AgentModel;
import dev.aliencode.core.mission.domain.model.Delivery;
import dev.aliencode.core.mission.domain.model.DeliveryStatus;
import dev.aliencode.core.mission.domain.model.EventType;
import dev.aliencode.core.mission.domain.model.Mission;
import dev.aliencode.core.mission.domain.model.MissionId;
import dev.aliencode.core.mission.domain.model.MissionStatus;
import dev.aliencode.core.toca.domain.model.ChangedFile;
import dev.aliencode.core.toca.domain.model.RepositorySeed;
import dev.aliencode.core.toca.domain.model.Seed;
import dev.aliencode.core.toca.domain.model.TocaId;
import dev.aliencode.core.toca.domain.model.WorkspaceChanges;

import static dev.aliencode.core.mission.application.MissionTestDoubles.BASE_COMMIT;
import static dev.aliencode.core.mission.application.MissionTestDoubles.HEAD_COMMIT;
import static dev.aliencode.core.mission.application.MissionTestDoubles.NOW;
import static dev.aliencode.core.mission.application.MissionTestDoubles.PATCH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewDeliveryServiceTest {

    private final InMemoryEventStore store = new InMemoryEventStore();
    private final InMemoryMissions missions = new InMemoryMissions();
    private final InMemoryDeliveries deliveries = new InMemoryDeliveries();
    private final MissionEventHub hub = new MissionEventHub(this.store, this.missions);
    private final FakeDeliveryTarget target = new FakeDeliveryTarget();

    private final ReviewDeliveryService service = new ReviewDeliveryService(
            this.missions,
            this.deliveries,
            this.target,
            new MissionTransitions(this.missions, this.hub),
            this.hub,
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void aprovarAplicaNaBranchEConcluiAMissao() {
        Mission mission = this.awaitingReview();

        Delivery applied = this.service.approve(mission.id());

        assertThat(applied.status()).isEqualTo(DeliveryStatus.APPLIED);
        assertThat(applied.headCommit()).isEqualTo(HEAD_COMMIT);
        assertThat(this.target.branches).containsExactly("alien/" + mission.id().value());
        assertThat(this.deliveries.findByMissionId(mission.id()).orElseThrow().status()).isEqualTo(DeliveryStatus.APPLIED);
        assertThat(this.missions.findById(mission.id()).orElseThrow().status()).isEqualTo(MissionStatus.COMPLETED);
        assertThat(this.store.types()).containsExactly("delivery.applied", "mission.state");
        assertThat(this.store.ofType(EventType.DELIVERY_APPLIED).getFirst().payload()).containsEntry("headCommit", HEAD_COMMIT);
    }

    @Test
    void descartarNaoTocaNoRepositorio() {
        Mission mission = this.awaitingReview();

        Delivery rejected = this.service.reject(mission.id());

        assertThat(rejected.status()).isEqualTo(DeliveryStatus.REJECTED);
        assertThat(this.target.branches).isEmpty();
        assertThat(this.missions.findById(mission.id()).orElseThrow().status()).isEqualTo(MissionStatus.REJECTED);
        assertThat(this.store.types()).containsExactly("delivery.rejected", "mission.state");
    }

    @Test
    void entregaJaResolvidaNaoEhAplicadaDeNovo() {
        Mission mission = this.awaitingReview();

        this.service.approve(mission.id());

        assertThatThrownBy(() -> this.service.approve(mission.id())).isInstanceOf(DeliveryConflictException.class);
        assertThatThrownBy(() -> this.service.reject(mission.id())).isInstanceOf(DeliveryConflictException.class);
        assertThat(this.target.branches).hasSize(1);
    }

    @Test
    void gitRecusandoMantemAEntregaPendente() {
        Mission mission = this.awaitingReview();

        this.target.failure = new DeliveryConflictException("A branch alien/m-42 já existe");

        assertThatThrownBy(() -> this.service.approve(mission.id())).hasMessageContaining("já existe");
        assertThat(this.deliveries.findByMissionId(mission.id()).orElseThrow().status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(this.missions.findById(mission.id()).orElseThrow().status()).isEqualTo(MissionStatus.AWAITING_REVIEW);
        assertThat(this.store.events).isEmpty();
    }

    private Mission awaitingReview() {
        TocaId toca = TocaId.newId();
        Seed seed = new Seed.ExistingRepositories(List.of(new RepositorySeed("calc", Path.of("/tmp/calc"), null)));
        Mission mission = Mission.create(
                MissionId.newId(),
                "Soma",
                "Corrija a soma",
                seed,
                AgentModel.parse("ollama/qwen3:8b"),
                NOW
        )
                .provisioning()
                .withToca(toca)
                .executing(toca, "ses_1")
                .awaitingReview();

        WorkspaceChanges changes = new WorkspaceChanges(BASE_COMMIT, PATCH, List.of(new ChangedFile("calc.py", 1, 1)));

        this.missions.save(mission);
        this.deliveries.save(
                Delivery.pending(
                        mission.id(),
                        "calc",
                        Path.of("/tmp/calc"),
                        changes,
                        NOW
                )
        );

        return mission;
    }
}
