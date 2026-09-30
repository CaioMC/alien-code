package dev.aliencode.adapters.mission.websocket.mapper;

import org.springframework.stereotype.Component;

import dev.aliencode.adapters.mission.websocket.message.AlienEventMessage;
import dev.aliencode.core.mission.domain.model.AlienEvent;

@Component
public class AlienEventMessageMapper {

    public AlienEventMessage toMessage(AlienEvent event) {
        return new AlienEventMessage(
                AlienEvent.VERSION,
                event.missionId().value(),
                event.seq(),
                event.ts(),
                event.type().wireName(),
                event.stepId(),
                event.parentStepId(),
                event.source().wireName(),
                event.payload()
        );
    }
}
