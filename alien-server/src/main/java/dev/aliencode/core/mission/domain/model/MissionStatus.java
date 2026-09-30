package dev.aliencode.core.mission.domain.model;

/**
 * Estados da missão no M1 (especificação, seção 9.2, sem clarificação, plano e entrega).
 * As transições são decididas pelo orquestrador, nunca pelo modelo.
 */
public enum MissionStatus {
    /** Registrada; ainda não começou a provisionar. */
    CREATED,
    /** Criando e semeando a Toca. */
    PROVISIONING,
    /** O agente está trabalhando na tarefa. */
    EXECUTING,
    /** O agente terminou a tarefa (sessão ociosa sem erro). */
    COMPLETED,
    /** Erro de infraestrutura, do agente ou tempo esgotado. */
    FAILED,
    /** Parada pelo usuário. */
    CANCELLED;

    public boolean isActive() {
        return this == CREATED || this == PROVISIONING || this == EXECUTING;
    }
}
