package dev.aliencode.adapters.mission.web.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.aliencode.adapters.mission.web.mapper.MissionWebMapper;
import dev.aliencode.adapters.mission.web.request.StartMissionRequest;
import dev.aliencode.adapters.mission.web.response.MissionResponse;
import dev.aliencode.core.mission.domain.model.MissionId;
import dev.aliencode.core.mission.usecase.GetMissionUseCase;
import dev.aliencode.core.mission.usecase.StartMissionUseCase;
import dev.aliencode.core.mission.usecase.StopMissionUseCase;

/** API REST das missões (especificação, seção 10.2). A timeline ao vivo vem pelo WebSocket. */
@RestController
@RequestMapping("/api/missions")
public class MissionController {

    private final StartMissionUseCase start;
    private final GetMissionUseCase get;
    private final StopMissionUseCase stop;

    private final MissionWebMapper mapper;

    public MissionController(
            StartMissionUseCase start,
            GetMissionUseCase get,
            StopMissionUseCase stop,
            MissionWebMapper mapper
    ) {
        this.start = start;
        this.get = get;
        this.stop = stop;
        this.mapper = mapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MissionResponse start(@RequestBody StartMissionRequest request) {
        return this.mapper.toResponse(
                this.start.start(
                        this.mapper.toCommand(request)
                )
        );
    }

    @GetMapping
    public List<MissionResponse> list() {
        return this.mapper.toResponses(this.get.list());
    }

    @GetMapping("/{id}")
    public MissionResponse get(@PathVariable String id) {
        MissionId missionId = new MissionId(id);

        return this.mapper.toResponse(
                this.get.get(missionId),
                this.get.lastSeq(missionId)
        );
    }

    @PostMapping("/{id}/stop")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MissionResponse stop(@PathVariable String id) {
        return this.mapper.toResponse(
                this.stop.stop(new MissionId(id))
        );
    }
}
