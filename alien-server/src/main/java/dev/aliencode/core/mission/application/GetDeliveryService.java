package dev.aliencode.core.mission.application;

import org.springframework.stereotype.Service;

import dev.aliencode.core.mission.domain.exception.DeliveryNotFoundException;
import dev.aliencode.core.mission.domain.model.Delivery;
import dev.aliencode.core.mission.domain.model.MissionId;
import dev.aliencode.core.mission.port.repository.DeliveryRepository;
import dev.aliencode.core.mission.usecase.GetDeliveryUseCase;

@Service
public class GetDeliveryService implements GetDeliveryUseCase {

    private final DeliveryRepository deliveries;

    public GetDeliveryService(DeliveryRepository deliveries) {
        this.deliveries = deliveries;
    }

    @Override
    public Delivery get(MissionId id) {
        return this.deliveries.findByMissionId(id).orElseThrow(() -> new DeliveryNotFoundException(id));
    }
}
