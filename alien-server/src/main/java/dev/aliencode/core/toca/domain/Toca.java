package dev.aliencode.core.toca.domain;

import java.time.Instant;
import java.util.List;

/**
 * O ambiente efêmero de uma missão: um container com opencode e o workspace semeado.
 * Imutável; cada transição devolve uma nova instância.
 */
public record Toca(
        TocaId id,
        String missionId,
        TocaStatus status,
        String containerId,
        TocaEndpoint endpoint,
        List<String> workspaceDirs,
        Instant createdAt,
        Instant expiresAt,
        String failureReason) {

    public Toca {
        if (id == null || status == null || createdAt == null || expiresAt == null) {
            throw new IllegalArgumentException("Toca incompleta");
        }
        workspaceDirs = workspaceDirs == null ? List.of() : List.copyOf(workspaceDirs);
    }

    public static Toca provisioning(TocaId id, String missionId, Instant now, Instant expiresAt) {
        return new Toca(id, missionId, TocaStatus.PROVISIONING, null, null, List.of(), now, expiresAt, null);
    }

    public Toca withContainer(String containerId, TocaEndpoint endpoint) {
        requireStatus(TocaStatus.PROVISIONING);
        return new Toca(this.id, this.missionId, this.status, containerId, endpoint, this.workspaceDirs,
                this.createdAt, this.expiresAt, null);
    }

    public Toca ready(List<String> workspaceDirs) {
        requireStatus(TocaStatus.PROVISIONING);
        if (this.containerId == null) {
            throw new IllegalStateException("A Toca " + this.id + " não pode ficar pronta sem container");
        }
        return new Toca(this.id, this.missionId, TocaStatus.READY, this.containerId, this.endpoint, workspaceDirs,
                this.createdAt, this.expiresAt, null);
    }

    public Toca failed(String reason) {
        return new Toca(this.id, this.missionId, TocaStatus.FAILED, this.containerId, this.endpoint,
                this.workspaceDirs, this.createdAt, this.expiresAt, reason);
    }

    public Toca disposed() {
        return new Toca(this.id, this.missionId, TocaStatus.DISPOSED, this.containerId, this.endpoint,
                this.workspaceDirs, this.createdAt, this.expiresAt, this.failureReason);
    }

    public boolean isExpired(Instant now) {
        return this.status.isActive() && !now.isBefore(this.expiresAt);
    }

    public boolean hasContainer() {
        return this.containerId != null;
    }

    private void requireStatus(TocaStatus expected) {
        if (this.status != expected) {
            throw new IllegalStateException(
                    "A Toca " + this.id + " está em " + this.status + ", esperado " + expected);
        }
    }
}
