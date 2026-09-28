package dev.aliencode.core.toca.domain;

public class TocaNotFoundException extends RuntimeException {

    public TocaNotFoundException(TocaId id) {
        super("Toca não encontrada: " + id);
    }
}
