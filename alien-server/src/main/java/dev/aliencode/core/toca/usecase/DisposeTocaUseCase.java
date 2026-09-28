package dev.aliencode.core.toca.usecase;

import dev.aliencode.core.toca.domain.Toca;
import dev.aliencode.core.toca.domain.TocaId;

public interface DisposeTocaUseCase {

    Toca dispose(TocaId id);
}
