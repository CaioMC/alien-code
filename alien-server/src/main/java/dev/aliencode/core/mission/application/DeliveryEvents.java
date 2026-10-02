package dev.aliencode.core.mission.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.aliencode.core.mission.domain.model.Delivery;
import dev.aliencode.core.mission.domain.model.EventSource;
import dev.aliencode.core.mission.domain.model.EventType;
import dev.aliencode.core.mission.domain.model.NewEvent;

/** Eventos da entrega na timeline. O patch completo não vai no evento: a UI busca em GET .../delivery. */
final class DeliveryEvents {

    static final String STEP = "delivery";

    private DeliveryEvents() {
    }

    static NewEvent ready(Delivery delivery) {
        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put("title", "Entrega aguardando revisão");
        payload.put("repository", delivery.repository());
        payload.put("branch", delivery.branch());
        payload.put("baseCommit", delivery.baseCommit());
        payload.put("additions", delivery.additions());
        payload.put("deletions", delivery.deletions());
        payload.put("files", files(delivery));

        return event(EventType.DELIVERY_READY, payload);
    }

    static NewEvent applied(Delivery delivery) {
        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put("title", "Entrega aplicada em " + delivery.branch());
        payload.put("branch", delivery.branch());
        payload.put("headCommit", delivery.headCommit());
        payload.put("repositoryPath", delivery.repositoryPath().toString());

        return event(EventType.DELIVERY_APPLIED, payload);
    }

    static NewEvent rejected(Delivery delivery) {
        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put("title", "Entrega descartada");
        payload.put("branch", delivery.branch());

        return event(EventType.DELIVERY_REJECTED, payload);
    }

    private static List<Map<String, Object>> files(Delivery delivery) {
        return delivery.files().stream()
                .map(file -> Map.<String, Object>of("path", file.path(), "additions", file.additions(), "deletions", file.deletions()))
                .toList();
    }

    private static NewEvent event(
            EventType type,
            Map<String, Object> payload
    ) {
        return new NewEvent(
                type,
                STEP,
                null,
                EventSource.ALIEN,
                payload
        );
    }
}
