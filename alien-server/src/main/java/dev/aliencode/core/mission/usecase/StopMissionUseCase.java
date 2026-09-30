package dev.aliencode.core.mission.usecase;

import dev.aliencode.core.mission.domain.model.Mission;
import dev.aliencode.core.mission.domain.model.MissionId;

public interface StopMissionUseCase {

    /**
     * Pede a parada da missão: aborta a sessão do agente e descarta a Toca. A missão termina
     * como CANCELLED em segundo plano; o retorno é o estado no momento do pedido.
     */
    Mission stop(MissionId id);
}
