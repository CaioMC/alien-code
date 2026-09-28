package dev.aliencode.core.toca.application;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import dev.aliencode.core.toca.domain.Toca;
import dev.aliencode.core.toca.domain.TocaId;
import dev.aliencode.core.toca.domain.TocaNotFoundException;
import dev.aliencode.core.toca.port.TocaRepository;
import dev.aliencode.core.toca.usecase.ListTocasUseCase;

@Service
public class ListTocasService implements ListTocasUseCase {

    private final TocaRepository tocas;

    public ListTocasService(TocaRepository tocas) {
        this.tocas = tocas;
    }

    @Override
    public List<Toca> list() {
        return this.tocas.findAll().stream()
                .sorted(Comparator.comparing(Toca::createdAt).reversed())
                .toList();
    }

    @Override
    public Toca get(TocaId id) {
        return this.tocas.findById(id).orElseThrow(() -> new TocaNotFoundException(id));
    }
}
