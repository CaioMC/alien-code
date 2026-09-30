package dev.aliencode.core.mission.domain.exception;

import dev.aliencode.core.mission.domain.model.MissionId;

public class MissionNotFoundException extends RuntimeException {

    public MissionNotFoundException(MissionId id) {
        super("Missão não encontrada: " + id);
    }
}
