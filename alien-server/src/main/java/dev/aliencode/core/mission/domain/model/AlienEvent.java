package dev.aliencode.core.mission.domain.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * O envelope único da timeline (especificação, seção 8.1). {@code seq} é monotônico por missão
 * e é a base do replay: quem reconecta com {@code lastSeq=N} recebe N+1 em diante.
 */
public record AlienEvent(
        MissionId missionId,
        long seq,
        Instant ts,
        EventType type,
        String stepId,
        String parentStepId,
        EventSource source,
        Map<String, Object> payload
) {

    public static final int VERSION = 1;

    public AlienEvent {
        if (isNull(missionId) || isNull(ts) || isNull(type) || isNull(source) || seq < 1) {
            throw new IllegalArgumentException("Evento incompleto");
        }

        payload = withoutNulls(payload);
    }

    /** Campos nulos somem do payload: o JSON fica menor e {@link Map#copyOf} não aceita nulos. */
    private static Map<String, Object> withoutNulls(Map<String, Object> payload) {
        if (isNull(payload)) {
            return Map.of();
        }

        Map<String, Object> copy = new LinkedHashMap<>();

        payload.forEach((key, value) -> {
            if (nonNull(value)) {
                copy.put(key, value);
            }
        });

        return Collections.unmodifiableMap(copy);
    }
}
