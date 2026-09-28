package dev.aliencode.adapters.toca.persistence;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import dev.aliencode.core.toca.domain.Toca;
import dev.aliencode.core.toca.domain.TocaId;
import dev.aliencode.core.toca.port.TocaRepository;

/**
 * Em memória por enquanto: ao reiniciar o servidor, as Tocas antigas viram órfãs
 * e o faxineiro remove os containers. O Event Store em SQLite chega com as missões (M1).
 */
@Repository
public class InMemoryTocaRepository implements TocaRepository {

    private final Map<TocaId, Toca> tocas = new ConcurrentHashMap<>();

    @Override
    public void save(Toca toca) {
        this.tocas.put(toca.id(), toca);
    }

    @Override
    public Optional<Toca> findById(TocaId id) {
        return Optional.ofNullable(this.tocas.get(id));
    }

    @Override
    public List<Toca> findAll() {
        return List.copyOf(this.tocas.values());
    }
}
