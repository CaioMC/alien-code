package dev.aliencode.adapters.mission.web.response;

import java.time.Instant;

/** @param lastSeq último evento gravado; o cliente conecta no WebSocket com esse valor para não receber de novo */
public record MissionResponse(
        String id,
        String title,
        String prompt,
        String model,
        String status,
        String tocaId,
        Instant createdAt,
        Instant finishedAt,
        String failureReason,
        Long lastSeq
) {
}
