package dev.aliencode.adapters.mission.web.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.aliencode.adapters.mission.web.request.StartMissionRequest;
import dev.aliencode.adapters.mission.web.response.MissionResponse;
import dev.aliencode.adapters.toca.web.mapper.TocaWebMapper;
import dev.aliencode.adapters.toca.web.request.SeedRequest;
import dev.aliencode.core.mission.domain.model.Mission;
import dev.aliencode.core.mission.usecase.command.StartMissionCommand;

import static java.util.Objects.isNull;

/** Tradução entre o formato HTTP das missões e o domínio. A semeadura reaproveita o formato das Tocas. */
@Component
public class MissionWebMapper {

    private final TocaWebMapper tocas;

    public MissionWebMapper(TocaWebMapper tocas) {
        this.tocas = tocas;
    }

    public StartMissionCommand toCommand(StartMissionRequest request) {
        if (isNull(request) || isNull(request.seed()) || isNull(request.seed().type())) {
            throw new IllegalArgumentException("Informe seed.type: '" + SeedRequest.EXISTING + "' ou '" + SeedRequest.NEW + "'");
        }

        return new StartMissionCommand(
                request.title(),
                request.prompt(),
                this.tocas.toSeed(request.seed()),
                request.model()
        );
    }

    public MissionResponse toResponse(Mission mission) {
        return this.toResponse(mission, null);
    }

    public MissionResponse toResponse(
            Mission mission,
            Long lastSeq
    ) {
        return new MissionResponse(
                mission.id().value(),
                mission.title(),
                mission.prompt(),
                mission.model().toString(),
                mission.status().name(),
                mission.hasToca() ? mission.tocaId().value() : null,
                mission.createdAt(),
                mission.finishedAt(),
                mission.failureReason(),
                lastSeq
        );
    }

    public List<MissionResponse> toResponses(List<Mission> missions) {
        return missions.stream().map(this::toResponse).toList();
    }
}
