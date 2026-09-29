package dev.aliencode.core.toca.usecase;

import dev.aliencode.core.toca.domain.model.Toca;
import dev.aliencode.core.toca.domain.model.TocaId;

public interface DisposeTocaUseCase {

    Toca dispose(TocaId id);
}
