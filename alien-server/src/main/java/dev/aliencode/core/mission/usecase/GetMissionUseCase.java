package dev.aliencode.core.mission.usecase;

import java.util.List;

import dev.aliencode.core.mission.domain.model.Mission;
import dev.aliencode.core.mission.domain.model.MissionId;

public interface GetMissionUseCase {

    Mission get(MissionId id);

    List<Mission> list();

    /** Último {@code seq} gravado da missão (0 se ainda não há eventos). */
    long lastSeq(MissionId id);
}
