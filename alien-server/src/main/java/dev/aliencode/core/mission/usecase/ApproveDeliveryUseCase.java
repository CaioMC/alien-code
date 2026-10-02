package dev.aliencode.core.mission.usecase;

import dev.aliencode.core.mission.domain.model.Delivery;
import dev.aliencode.core.mission.domain.model.MissionId;

/** O dev aprova a entrega: o patch vira commits numa branch {@code alien/<missão>} do repositório original. */
public interface ApproveDeliveryUseCase {

    Delivery approve(MissionId id);
}
