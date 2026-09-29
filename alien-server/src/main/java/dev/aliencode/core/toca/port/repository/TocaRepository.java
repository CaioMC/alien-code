package dev.aliencode.core.toca.port.repository;

import java.util.List;
import java.util.Optional;

import dev.aliencode.core.toca.domain.model.Toca;
import dev.aliencode.core.toca.domain.model.TocaId;

public interface TocaRepository {

    void save(Toca toca);

    Optional<Toca> findById(TocaId id);

    List<Toca> findAll();
}
