package dev.aliencode.core.mission.application;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import dev.aliencode.core.mission.domain.model.EventSource;
import dev.aliencode.core.mission.domain.model.EventType;
import dev.aliencode.core.mission.domain.model.Mission;
import dev.aliencode.core.mission.domain.model.NewEvent;
import dev.aliencode.core.mission.port.repository.MissionRepository;

/** Grava cada mudança de estado da missão e a anuncia na timeline ({@code mission.state}). */
@Service
public class MissionTransitions {

    private static final Logger log = LoggerFactory.getLogger(MissionTransitions.class);

    private final MissionRepository missions;
    private final MissionEventHub events;

    public MissionTransitions(MissionRepository missions, MissionEventHub events) {
        this.missions = missions;
        this.events = events;
    }

    public Mission record(Mission mission) {
        this.missions.save(mission);

        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put("status", mission.status().name());
        payload.put("tocaId", mission.hasToca() ? mission.tocaId().value() : null);
        payload.put("reason", mission.failureReason());

        this.events.publish(
                mission.id(),
                new NewEvent(
                        EventType.MISSION_STATE,
                        null,
                        null,
                        EventSource.ALIEN,
                        payload
                )
        );
        log.info("Missão {} → {}", mission.id(), mission.status());

        return mission;
    }
}
