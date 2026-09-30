package dev.aliencode.core.mission.application;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import dev.aliencode.core.mission.domain.exception.MissionNotFoundException;
import dev.aliencode.core.mission.domain.model.AlienEvent;
import dev.aliencode.core.mission.domain.model.MissionId;
import dev.aliencode.core.mission.domain.model.NewEvent;
import dev.aliencode.core.mission.port.event.EventStorePort;
import dev.aliencode.core.mission.port.repository.MissionRepository;
import dev.aliencode.core.mission.usecase.MissionEventListener;
import dev.aliencode.core.mission.usecase.MissionWatch;
import dev.aliencode.core.mission.usecase.WatchMissionUseCase;

/**
 * Hub de eventos: grava cada evento no Event Store e o entrega a quem acompanha a missão.
 *
 * <p>Gravar + distribuir e reproduzir + registrar acontecem sob o mesmo lock por missão:
 * quem reconecta recebe o histórico e depois o ao vivo, sem buraco nem repetição.
 */
@Service
public class MissionEventHub implements WatchMissionUseCase {

    private static final Logger log = LoggerFactory.getLogger(MissionEventHub.class);

    private final EventStorePort store;
    private final MissionRepository missions;

    private final Map<MissionId, Object> locks = new ConcurrentHashMap<>();
    private final Map<MissionId, Set<MissionEventListener>> listeners = new ConcurrentHashMap<>();

    public MissionEventHub(EventStorePort store, MissionRepository missions) {
        this.store = store;
        this.missions = missions;
    }

    public AlienEvent publish(
            MissionId id,
            NewEvent event
    ) {
        synchronized (this.lockFor(id)) {
            AlienEvent stored = this.store.append(id, event);

            for (MissionEventListener listener : this.listeners.getOrDefault(id, Set.of())) {
                this.deliver(listener, stored);
            }

            return stored;
        }
    }

    public void publishAll(
            MissionId id,
            List<NewEvent> events
    ) {
        events.forEach(event -> this.publish(id, event));
    }

    @Override
    public MissionWatch watch(
            MissionId id,
            long lastSeq,
            MissionEventListener listener
    ) {
        this.missions.findById(id).orElseThrow(() -> new MissionNotFoundException(id));

        synchronized (this.lockFor(id)) {
            for (AlienEvent event : this.store.findAfter(id, Math.max(0, lastSeq))) {
                this.deliver(listener, event);
            }

            this.listeners.computeIfAbsent(id, key -> new CopyOnWriteArraySet<>()).add(listener);
        }

        return () -> this.listeners.getOrDefault(id, Set.of()).remove(listener);
    }

    private Object lockFor(MissionId id) {
        return this.locks.computeIfAbsent(id, key -> new Object());
    }

    /** Um ouvinte com problema (ex.: WebSocket fechado) não pode parar a missão nem os outros ouvintes. */
    private void deliver(
            MissionEventListener listener,
            AlienEvent event
    ) {
        try {
            listener.onEvent(event);
        } catch (RuntimeException e) {
            log.debug("Ouvinte da missão {} falhou no evento {}: {}", event.missionId(), event.seq(), e.getMessage());
        }
    }
}
