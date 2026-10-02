package dev.aliencode.core.mission.usecase;

import dev.aliencode.core.mission.domain.model.Delivery;
import dev.aliencode.core.mission.domain.model.MissionId;

public interface GetDeliveryUseCase {

    Delivery get(MissionId id);
}
