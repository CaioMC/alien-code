package dev.aliencode.core.mission.usecase;

import dev.aliencode.core.mission.domain.model.Delivery;
import dev.aliencode.core.mission.domain.model.MissionId;

/** O dev descarta a entrega: nada chega ao repositório original. */
public interface RejectDeliveryUseCase {

    Delivery reject(MissionId id);
}
