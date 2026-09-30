package dev.aliencode.core.mission.domain.model;

import java.time.Instant;

import dev.aliencode.core.toca.domain.model.Seed;
import dev.aliencode.core.toca.domain.model.TocaId;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Uma unidade de trabalho pedida pelo usuário. No M1: um repositório (ou projeto novo),
 * uma tarefa, uma sessão do opencode. Imutável; cada transição devolve uma nova instância.
 *
 * @param prompt    o pedido do usuário, enviado ao agente como instrução da tarefa
 * @param tocaId    Toca da missão, depois de provisionada
 * @param sessionId sessão do opencode que executa a tarefa
 */
public record Mission(
        MissionId id,
        String title,
        String prompt,
        Seed seed,
        AgentModel model,
        MissionStatus status,
        TocaId tocaId,
        String sessionId,
        Instant createdAt,
        Instant finishedAt,
        String failureReason
) {

    public Mission {
        if (isNull(id) || isNull(seed) || isNull(model) || isNull(status) || isNull(createdAt)) {
            throw new IllegalArgumentException("Missão incompleta");
        }

        if (isBlank(prompt)) {
            throw new IllegalArgumentException("Descreva o que o agente deve fazer (prompt)");
        }

        if (isBlank(title)) {
            title = prompt.strip().lines().findFirst().orElse(prompt);
        }
    }

    public static Mission create(
            MissionId id,
            String title,
            String prompt,
            Seed seed,
            AgentModel model,
            Instant now
    ) {
        return new Mission(
                id,
                title,
                prompt,
                seed,
                model,
                MissionStatus.CREATED,
                null,
                null,
                now,
                null,
                null
        );
    }

    public Mission provisioning() {
        this.requireStatus(MissionStatus.CREATED);

        return this.with(
                MissionStatus.PROVISIONING,
                this.tocaId,
                this.sessionId,
                null,
                null
        );
    }

    public Mission executing(
            TocaId tocaId,
            String sessionId
    ) {
        this.requireStatus(MissionStatus.PROVISIONING);

        if (isNull(tocaId) || isBlank(sessionId)) {
            throw new IllegalArgumentException("A missão " + this.id + " só executa com Toca e sessão do agente");
        }

        return this.with(
                MissionStatus.EXECUTING,
                tocaId,
                sessionId,
                null,
                null
        );
    }

    /** Registra a Toca assim que ela existe, para que falhas posteriores ainda saibam o que descartar. */
    public Mission withToca(TocaId tocaId) {
        this.requireStatus(MissionStatus.PROVISIONING);

        return this.with(
                this.status,
                tocaId,
                this.sessionId,
                null,
                null
        );
    }

    public Mission completed(Instant now) {
        this.requireStatus(MissionStatus.EXECUTING);

        return this.with(
                MissionStatus.COMPLETED,
                this.tocaId,
                this.sessionId,
                now,
                null
        );
    }

    public Mission failed(
            String reason,
            Instant now
    ) {
        this.requireActive();

        return this.with(
                MissionStatus.FAILED,
                this.tocaId,
                this.sessionId,
                now,
                reason
        );
    }

    public Mission cancelled(Instant now) {
        this.requireActive();

        return this.with(
                MissionStatus.CANCELLED,
                this.tocaId,
                this.sessionId,
                now,
                null
        );
    }

    public boolean hasToca() {
        return nonNull(this.tocaId);
    }

    private Mission with(
            MissionStatus status,
            TocaId tocaId,
            String sessionId,
            Instant finishedAt,
            String failureReason
    ) {
        return new Mission(
                this.id,
                this.title,
                this.prompt,
                this.seed,
                this.model,
                status,
                tocaId,
                sessionId,
                this.createdAt,
                finishedAt,
                failureReason
        );
    }

    private void requireStatus(MissionStatus expected) {
        if (this.status != expected) {
            throw new IllegalStateException("A missão " + this.id + " está em " + this.status + ", esperado " + expected);
        }
    }

    private void requireActive() {
        if (!this.status.isActive()) {
            throw new IllegalStateException("A missão " + this.id + " já terminou (" + this.status + ")");
        }
    }
}
