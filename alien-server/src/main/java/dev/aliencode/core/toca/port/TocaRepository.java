package dev.aliencode.core.toca.port;

import dev.aliencode.core.toca.domain.Toca;
import dev.aliencode.core.toca.domain.TocaId;

import java.util.List;
import java.util.Optional;

public interface TocaRepository {

    void save(Toca toca);

    Optional<Toca> findById(TocaId id);

    List<Toca> findAll();
}
