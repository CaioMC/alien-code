package dev.aliencode.core.mission.application;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import dev.aliencode.core.mission.domain.exception.DeliveryConflictException;
import dev.aliencode.core.mission.domain.exception.DeliveryNotFoundException;
import dev.aliencode.core.mission.domain.exception.MissionNotFoundException;
import dev.aliencode.core.mission.domain.model.Delivery;
import dev.aliencode.core.mission.domain.model.Mission;
import dev.aliencode.core.mission.domain.model.MissionId;
import dev.aliencode.core.mission.domain.model.MissionStatus;
import dev.aliencode.core.mission.port.delivery.DeliveryPort;
import dev.aliencode.core.mission.port.repository.DeliveryRepository;
import dev.aliencode.core.mission.port.repository.MissionRepository;
import dev.aliencode.core.mission.usecase.ApproveDeliveryUseCase;
import dev.aliencode.core.mission.usecase.RejectDeliveryUseCase;

/**
 * A revisão da entrega pelo dev: aplicar numa branch local ou descartar.
 *
 * <p>Os dois métodos são sincronizados na mesma instância: dois cliques (ou "Aplicar" e
 * "Descartar" ao mesmo tempo) não resolvem a entrega duas vezes. Se o git recusar a aplicação
 * (branch já existe, commit base ausente), nada muda e a entrega continua pendente.
 */
@Service
public class ReviewDeliveryService implements ApproveDeliveryUseCase, RejectDeliveryUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReviewDeliveryService.class);

    private final MissionRepository missions;
    private final DeliveryRepository deliveries;
    private final DeliveryPort target;
    private final MissionTransitions transitions;
    private final MissionEventHub events;
    private final Clock clock;

    public ReviewDeliveryService(
            MissionRepository missions,
            DeliveryRepository deliveries,
            DeliveryPort target,
            MissionTransitions transitions,
            MissionEventHub events,
            Clock clock
    ) {
        this.missions = missions;
        this.deliveries = deliveries;
        this.target = target;
        this.transitions = transitions;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public synchronized Delivery approve(MissionId id) {
        Mission mission = this.awaitingReview(id);
        Delivery delivery = this.pendingDelivery(mission);

        String headCommit = this.target.applyToBranch(
                delivery.repositoryPath(),
                delivery.baseCommit(),
                delivery.branch(),
                delivery.patch()
        );

        Instant now = this.clock.instant();
        Delivery applied = delivery.applied(headCommit, now);

        this.deliveries.save(applied);
        this.events.publish(id, DeliveryEvents.applied(applied));
        this.transitions.record(mission.delivered(now));
        log.info("Missão {}: entrega aplicada em {} ({}) de {}", id, applied.branch(), headCommit, applied.repositoryPath());

        return applied;
    }

    @Override
    public synchronized Delivery reject(MissionId id) {
        Mission mission = this.awaitingReview(id);
        Delivery delivery = this.pendingDelivery(mission);

        Instant now = this.clock.instant();
        Delivery rejected = delivery.rejected(now);

        this.deliveries.save(rejected);
        this.events.publish(id, DeliveryEvents.rejected(rejected));
        this.transitions.record(mission.rejected(now));
        log.info("Missão {}: entrega descartada", id);

        return rejected;
    }

    private Mission awaitingReview(MissionId id) {
        Mission mission = this.missions.findById(id).orElseThrow(() -> new MissionNotFoundException(id));

        if (mission.status() != MissionStatus.AWAITING_REVIEW) {
            throw new DeliveryConflictException("A missão " + id + " não está esperando revisão (" + mission.status() + ")");
        }

        return mission;
    }

    private Delivery pendingDelivery(Mission mission) {
        Delivery delivery = this.deliveries.findByMissionId(mission.id()).orElseThrow(() -> new DeliveryNotFoundException(mission.id()));

        if (!delivery.isPending()) {
            throw new DeliveryConflictException("A entrega da missão " + mission.id() + " já foi resolvida (" + delivery.status() + ")");
        }

        return delivery;
    }
}
