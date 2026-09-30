package dev.aliencode.core.mission.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * Um evento da timeline antes de ser gravado: o Event Store atribui {@code seq} e {@code ts}.
 *
 * @param stepId       passo desta linha na timeline (ex.: {@code t1.tool.call_7})
 * @param parentStepId passo que agrupa esta linha (ex.: {@code t1}); nulo na raiz
 */
public record NewEvent(
        EventType type,
        String stepId,
        String parentStepId,
        EventSource source,
        Map<String, Object> payload
) {

    public NewEvent {
        if (isNull(type) || isNull(source)) {
            throw new IllegalArgumentException("Evento sem tipo ou origem");
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
