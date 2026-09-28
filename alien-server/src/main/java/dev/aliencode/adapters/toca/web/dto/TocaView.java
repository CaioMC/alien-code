package dev.aliencode.adapters.toca.web.dto;

import java.time.Instant;
import java.util.List;

import dev.aliencode.core.toca.domain.Toca;

/** O que a API mostra de uma Toca. A senha do opencode fica de fora de propósito. */
public record TocaView(
        String id,
        String missionId,
        String status,
        String containerId,
        String agentUrl,
        List<String> workspace,
        Instant createdAt,
        Instant expiresAt,
        String failureReason) {

    public static TocaView from(Toca toca) {
        return new TocaView(
                toca.id().value(),
                toca.missionId(),
                toca.status().name(),
                toca.containerId() == null ? null : toca.containerId().substring(0, Math.min(12, toca.containerId().length())),
                toca.endpoint() == null ? null : toca.endpoint().baseUrl().toString(),
                toca.workspaceDirs(),
                toca.createdAt(),
                toca.expiresAt(),
                toca.failureReason());
    }
}
