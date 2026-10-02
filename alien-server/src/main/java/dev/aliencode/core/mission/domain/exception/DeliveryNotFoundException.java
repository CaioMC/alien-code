package dev.aliencode.core.mission.domain.exception;

import dev.aliencode.core.mission.domain.model.MissionId;

public class DeliveryNotFoundException extends RuntimeException {

    public DeliveryNotFoundException(MissionId id) {
        super("A missão " + id + " não tem entrega");
    }
}
