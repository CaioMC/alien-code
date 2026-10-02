package dev.aliencode.core.mission.domain.model;

/**
 * Estados da missão (especificação, seção 9.2, ainda sem clarificação e plano).
 * As transições são decididas pelo orquestrador ou pelo dev, nunca pelo modelo.
 */
public enum MissionStatus {
    /** Registrada; ainda não começou a provisionar. */
    CREATED,
    /** Criando e semeando a Toca. */
    PROVISIONING,
    /** O agente está trabalhando na tarefa. */
    EXECUTING,
    /** O agente terminou e deixou um patch; a missão espera o dev aplicar ou descartar a entrega. */
    AWAITING_REVIEW,
    /** O agente terminou sem alterações, ou a entrega foi aplicada numa branch local. */
    COMPLETED,
    /** Erro de infraestrutura, do agente ou tempo esgotado. */
    FAILED,
    /** Parada pelo usuário. */
    CANCELLED,
    /** O dev descartou a entrega. */
    REJECTED;

    /** Missão em andamento, conduzida pelo orquestrador. A revisão da entrega não conta: ela espera o dev, não o agente. */
    public boolean isActive() {
        return this == CREATED || this == PROVISIONING || this == EXECUTING;
    }
}
