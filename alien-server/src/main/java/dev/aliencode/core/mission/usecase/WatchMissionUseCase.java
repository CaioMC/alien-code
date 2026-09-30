package dev.aliencode.core.mission.usecase;

import dev.aliencode.core.mission.domain.model.MissionId;

public interface WatchMissionUseCase {

    /**
     * Entrega ao listener os eventos com {@code seq > lastSeq} já gravados e, em seguida, os novos,
     * na ordem, sem buracos nem repetições (reconexão da especificação, seção 8.4).
     */
    MissionWatch watch(
            MissionId id,
            long lastSeq,
            MissionEventListener listener
    );
}
