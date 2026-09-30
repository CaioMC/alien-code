package dev.aliencode.core.mission.application;

import java.util.List;

import org.springframework.stereotype.Service;

import dev.aliencode.core.mission.domain.exception.MissionNotFoundException;
import dev.aliencode.core.mission.domain.model.AlienEvent;
import dev.aliencode.core.mission.domain.model.Mission;
import dev.aliencode.core.mission.domain.model.MissionId;
import dev.aliencode.core.mission.port.event.EventStorePort;
import dev.aliencode.core.mission.port.repository.MissionRepository;
import dev.aliencode.core.mission.usecase.GetMissionUseCase;

@Service
public class GetMissionService implements GetMissionUseCase {

    private final MissionRepository missions;
    private final EventStorePort store;

    public GetMissionService(MissionRepository missions, EventStorePort store) {
        this.missions = missions;
        this.store = store;
    }

    @Override
    public Mission get(MissionId id) {
        return this.missions.findById(id).orElseThrow(() -> new MissionNotFoundException(id));
    }

    @Override
    public List<Mission> list() {
        return this.missions.findAll();
    }

    @Override
    public long lastSeq(MissionId id) {
        List<AlienEvent> events = this.store.findAfter(id, 0);

        return events.isEmpty() ? 0 : events.getLast().seq();
    }
}
