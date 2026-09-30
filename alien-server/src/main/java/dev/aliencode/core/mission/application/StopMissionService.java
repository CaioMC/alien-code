package dev.aliencode.core.mission.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import dev.aliencode.core.mission.domain.exception.MissionNotFoundException;
import dev.aliencode.core.mission.domain.model.Mission;
import dev.aliencode.core.mission.domain.model.MissionId;
import dev.aliencode.core.mission.port.repository.MissionRepository;
import dev.aliencode.core.mission.usecase.StopMissionUseCase;

@Service
public class StopMissionService implements StopMissionUseCase {

    private static final Logger log = LoggerFactory.getLogger(StopMissionService.class);

    private final MissionRepository missions;
    private final MissionConductor conductor;

    public StopMissionService(MissionRepository missions, MissionConductor conductor) {
        this.missions = missions;
        this.conductor = conductor;
    }

    @Override
    public Mission stop(MissionId id) {
        Mission mission = this.missions.findById(id).orElseThrow(() -> new MissionNotFoundException(id));

        if (!mission.status().isActive()) {
            return mission;
        }

        if (this.conductor.requestStop(id)) {
            log.info("Parada da missão {} pedida", id);
        }

        return mission;
    }
}
