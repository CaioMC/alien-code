package dev.aliencode.core.mission.usecase;

import dev.aliencode.core.mission.domain.model.Mission;
import dev.aliencode.core.mission.usecase.command.StartMissionCommand;

public interface StartMissionUseCase {

    /**
     * Registra a missão e começa a conduzi-la em segundo plano. Volta imediatamente, no estado
     * CREATED; o progresso é acompanhado pelos eventos ({@link WatchMissionUseCase}).
     */
    Mission start(StartMissionCommand command);
}
