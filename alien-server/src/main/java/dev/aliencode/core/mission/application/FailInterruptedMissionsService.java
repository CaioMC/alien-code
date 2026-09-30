package dev.aliencode.core.mission.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import dev.aliencode.core.mission.domain.model.EventSource;
import dev.aliencode.core.mission.domain.model.EventType;
import dev.aliencode.core.mission.domain.model.Mission;
import dev.aliencode.core.mission.domain.model.NewEvent;
import dev.aliencode.core.mission.port.repository.MissionRepository;
import dev.aliencode.core.mission.usecase.FailInterruptedMissionsUseCase;

@Service
public class FailInterruptedMissionsService implements FailInterruptedMissionsUseCase {

    private static final Logger log = LoggerFactory.getLogger(FailInterruptedMissionsService.class);

    static final String REASON = "O servidor foi reiniciado durante a missão";

    private final MissionRepository missions;
    private final MissionEventHub events;
    private final MissionConductor conductor;
    private final Clock clock;

    public FailInterruptedMissionsService(
            MissionRepository missions,
            MissionEventHub events,
            MissionConductor conductor,
            Clock clock
    ) {
        this.missions = missions;
        this.events = events;
        this.conductor = conductor;
        this.clock = clock;
    }

    @Override
    public List<String> failInterrupted() {
        List<String> failed = new ArrayList<>();

        for (Mission mission : this.missions.findAll()) {
            if (!mission.status().isActive() || this.conductor.isRunning(mission.id())) {
                continue;
            }

            Mission interrupted = mission.failed(REASON, this.clock.instant());

            this.missions.save(interrupted);
            this.events.publish(interrupted.id(), this.state(interrupted));
            log.info("Missão {} estava {} quando o servidor parou; marcada como FAILED", mission.id(), mission.status());

            failed.add(mission.id().value());
        }

        return failed;
    }

    private NewEvent state(Mission mission) {
        return new NewEvent(
                EventType.MISSION_STATE,
                null,
                null,
                EventSource.ALIEN,
                Map.of("status", mission.status().name(), "reason", REASON)
        );
    }
}
