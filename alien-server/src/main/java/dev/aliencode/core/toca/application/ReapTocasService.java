package dev.aliencode.core.toca.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import dev.aliencode.core.toca.domain.model.Toca;
import dev.aliencode.core.toca.port.repository.TocaRepository;
import dev.aliencode.core.toca.port.sandbox.ManagedSandbox;
import dev.aliencode.core.toca.port.sandbox.SandboxPort;
import dev.aliencode.core.toca.usecase.DisposeTocaUseCase;
import dev.aliencode.core.toca.usecase.ReapTocasUseCase;

@Service
public class ReapTocasService implements ReapTocasUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReapTocasService.class);

    private final TocaRepository tocas;
    private final SandboxPort sandbox;
    private final DisposeTocaUseCase dispose;
    private final Clock clock;

    public ReapTocasService(TocaRepository tocas, SandboxPort sandbox, DisposeTocaUseCase dispose, Clock clock) {
        this.tocas = tocas;
        this.sandbox = sandbox;
        this.dispose = dispose;
        this.clock = clock;
    }

    @Override
    public List<String> reapExpired() {
        Instant now = this.clock.instant();
        List<String> reaped = new ArrayList<>();
        for (Toca toca : this.tocas.findAll()) {
            if (toca.isExpired(now)) {
                log.info("{} passou do TTL ({}); descartando", toca.id(), toca.expiresAt());
                this.dispose.dispose(toca.id());
                reaped.add(toca.id().value());
            }
        }
        return reaped;
    }

    @Override
    public List<String> removeOrphans() {
        Set<String> active = this.tocas.findAll().stream()
                .filter(toca -> toca.status().isActive())
                .map(toca -> toca.id().value())
                .collect(Collectors.toSet());
        List<String> removed = new ArrayList<>();
        for (ManagedSandbox managed : this.sandbox.listManaged()) {
            if (!active.contains(managed.tocaId())) {
                log.info("Removendo container órfão {} ({})", managed.containerId(), managed.tocaId());
                this.sandbox.remove(managed.containerId());
                removed.add(managed.containerId());
            }
        }
        return removed;
    }
}
