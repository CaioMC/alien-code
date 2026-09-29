package dev.aliencode.core.toca.domain.exception;

import dev.aliencode.core.toca.domain.model.TocaId;

public class TocaNotFoundException extends RuntimeException {

    public TocaNotFoundException(TocaId id) {
        super("Toca não encontrada: " + id);
    }
}
