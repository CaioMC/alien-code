package dev.aliencode.core.toca.domain;

public enum TocaStatus {
    /** Container sendo criado, semeado e aguardando o opencode responder. */
    PROVISIONING,
    /** opencode respondendo; a Toca pode receber tarefas. */
    READY,
    /** Algo falhou no provisionamento; o container já foi removido. */
    FAILED,
    /** Container removido (fim da missão, cancelamento ou TTL). */
    DISPOSED;

    public boolean isActive() {
        return this == PROVISIONING || this == READY;
    }
}
