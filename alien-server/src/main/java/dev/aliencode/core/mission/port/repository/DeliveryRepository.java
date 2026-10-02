package dev.aliencode.core.mission.port.repository;

import java.util.Optional;

import dev.aliencode.core.mission.domain.model.Delivery;
import dev.aliencode.core.mission.domain.model.MissionId;

public interface DeliveryRepository {

    void save(Delivery delivery);

    Optional<Delivery> findByMissionId(MissionId id);
}
