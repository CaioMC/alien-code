package dev.aliencode.core.toca.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import dev.aliencode.core.toca.domain.exception.TocaNotFoundException;
import dev.aliencode.core.toca.domain.model.Toca;
import dev.aliencode.core.toca.domain.model.TocaId;
import dev.aliencode.core.toca.port.repository.TocaRepository;
import dev.aliencode.core.toca.port.sandbox.SandboxPort;
import dev.aliencode.core.toca.usecase.DisposeTocaUseCase;

/** Passo 7 do ciclo de vida: remove o container. Idempotente. */
@Service
public class DisposeTocaService implements DisposeTocaUseCase {

    private static final Logger log = LoggerFactory.getLogger(DisposeTocaService.class);

    private final SandboxPort sandbox;
    private final TocaRepository tocas;

    public DisposeTocaService(SandboxPort sandbox, TocaRepository tocas) {
        this.sandbox = sandbox;
        this.tocas = tocas;
    }

    @Override
    public Toca dispose(TocaId id) {
        Toca toca = this.tocas.findById(id).orElseThrow(() -> new TocaNotFoundException(id));

        if (!toca.status().isActive()) {
            return toca;
        }

        if (toca.hasContainer()) {
            this.sandbox.remove(toca.containerId());
        }

        Toca disposed = toca.disposed();

        this.tocas.save(disposed);
        log.info("{} descartada", id);

        return disposed;
    }
}