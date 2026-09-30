package dev.aliencode.core.mission.application;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import dev.aliencode.core.mission.domain.model.AgentModel;
import dev.aliencode.core.mission.domain.model.EventSource;
import dev.aliencode.core.mission.domain.model.EventType;
import dev.aliencode.core.mission.domain.model.Mission;
import dev.aliencode.core.mission.domain.model.MissionId;
import dev.aliencode.core.mission.domain.model.NewEvent;
import dev.aliencode.core.mission.port.repository.MissionRepository;
import dev.aliencode.core.mission.usecase.StartMissionUseCase;
import dev.aliencode.core.mission.usecase.command.StartMissionCommand;
import dev.aliencode.core.toca.domain.model.Seed;
import dev.aliencode.core.toca.usecase.ProvisionTocaUseCase;

import static org.apache.commons.lang3.StringUtils.isBlank;

@Service
public class StartMissionService implements StartMissionUseCase {

    private static final Logger log = LoggerFactory.getLogger(StartMissionService.class);

    private final MissionRepository missions;
    private final MissionEventHub events;
    private final MissionConductor conductor;
    private final ProvisionTocaUseCase provisionToca;

    private final MissionSettings settings;
    private final Clock clock;

    public StartMissionService(
            MissionRepository missions,
            MissionEventHub events,
            MissionConductor conductor,
            ProvisionTocaUseCase provisionToca,
            MissionSettings settings,
            Clock clock
    ) {
        this.missions = missions;
        this.events = events;
        this.conductor = conductor;
        this.provisionToca = provisionToca;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    public Mission start(StartMissionCommand command) {
        this.validateSeed(command.seed());

        AgentModel model = isBlank(command.model()) ? this.settings.defaultModel() : AgentModel.parse(command.model());

        Mission mission = Mission.create(
                MissionId.newId(),
                command.title(),
                command.prompt(),
                command.seed(),
                model,
                this.clock.instant()
        );

        this.missions.save(mission);
        this.events.publish(mission.id(), this.created(mission));
        log.info("Missão {} criada: {}", mission.id(), mission.title());

        this.conductor.conduct(mission);

        return mission;
    }

    /** No M1 a missão tem uma única tarefa, num único diretório do workspace. */
    private void validateSeed(Seed seed) {
        if (seed instanceof Seed.ExistingRepositories(var repositories) && repositories.size() > 1) {
            throw new IllegalArgumentException("Por enquanto a missão trabalha em um único repositório (multi-repo chega no M4)");
        }

        this.provisionToca.validate(seed);
    }

    private NewEvent created(Mission mission) {
        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put("title", mission.title());
        payload.put("prompt", mission.prompt());
        payload.put("model", mission.model().toString());
        payload.put("status", mission.status().name());

        return new NewEvent(
                EventType.MISSION_CREATED,
                null,
                null,
                EventSource.ALIEN,
                payload
        );
    }
}
