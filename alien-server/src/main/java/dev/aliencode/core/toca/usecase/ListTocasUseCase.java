package dev.aliencode.core.toca.usecase;

import java.util.List;

import dev.aliencode.core.toca.domain.model.Toca;
import dev.aliencode.core.toca.domain.model.TocaId;

public interface ListTocasUseCase {

    List<Toca> list();

    Toca get(TocaId id);
}
