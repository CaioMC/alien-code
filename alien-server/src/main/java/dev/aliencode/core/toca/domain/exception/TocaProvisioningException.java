package dev.aliencode.core.toca.domain.exception;

import dev.aliencode.core.toca.domain.model.Toca;

/** O provisionamento falhou; o container (se chegou a existir) já foi removido. */
public class TocaProvisioningException extends RuntimeException {

    private final transient Toca toca;

    public TocaProvisioningException(Toca toca, Throwable cause) {
        super("Falha ao provisionar a Toca " + toca.id() + ": " + toca.failureReason(), cause);
        this.toca = toca;
    }

    public Toca toca() {
        return this.toca;
    }
}
