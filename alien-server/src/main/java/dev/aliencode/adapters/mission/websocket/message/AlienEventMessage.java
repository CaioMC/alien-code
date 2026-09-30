package dev.aliencode.adapters.mission.websocket.message;

import java.time.Instant;
import java.util.Map;

/** Envelope enviado ao cliente (especificação, seção 8.1). */
public record AlienEventMessage(
        int v,
        String missionId,
        long seq,
        Instant ts,
        String type,
        String stepId,
        String parentStepId,
        String source,
        Map<String, Object> payload
) {
}
