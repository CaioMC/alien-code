package dev.aliencode.core.toca.usecase;

import dev.aliencode.core.toca.domain.Toca;
import dev.aliencode.core.toca.domain.TocaId;

import java.util.List;

public interface ListTocasUseCase {

    List<Toca> list();

    Toca get(TocaId id);
}
