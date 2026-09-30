package dev.aliencode.core.mission.port.event;

import java.util.List;

import dev.aliencode.core.mission.domain.model.AlienEvent;
import dev.aliencode.core.mission.domain.model.MissionId;
import dev.aliencode.core.mission.domain.model.NewEvent;

/** Event Store append-only: a história completa de cada missão, em ordem (RNF-05). */
public interface EventStorePort {

    /** Grava o evento com o próximo {@code seq} da missão. */
    AlienEvent append(
            MissionId missionId,
            NewEvent event
    );

    /** Eventos da missão com {@code seq > lastSeq}, em ordem. */
    List<AlienEvent> findAfter(
            MissionId missionId,
            long lastSeq
    );
}
