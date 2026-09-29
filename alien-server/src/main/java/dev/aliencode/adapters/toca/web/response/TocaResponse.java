package dev.aliencode.adapters.toca.web.response;

import java.time.Instant;
import java.util.List;

/** O que a API mostra de uma Toca. A senha do opencode fica de fora de propósito. */
public record TocaResponse(
        String id,
        String missionId,
        String status,
        String containerId,
        String agentUrl,
        List<String> workspace,
        Instant createdAt,
        Instant expiresAt,
        String failureReason) {
}
