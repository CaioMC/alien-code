package dev.aliencode.adapters.toca.scheduling;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dev.aliencode.core.toca.usecase.ReapTocasUseCase;

/** Remove órfãos quando o servidor sobe e descarta Tocas vencidas periodicamente. */
@Component
public class TocaJanitor {

    private static final Logger log = LoggerFactory.getLogger(TocaJanitor.class);

    private final ReapTocasUseCase reap;

    public TocaJanitor(ReapTocasUseCase reap) {
        this.reap = reap;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void removeOrphansOnStartup() {
        try {
            List<String> removed = this.reap.removeOrphans();
            if (!removed.isEmpty()) {
                log.info("{} container(s) de Toca órfão(s) removido(s)", removed.size());
            }
        } catch (RuntimeException e) {
            log.warn("Não foi possível procurar Tocas órfãs (o Docker está rodando?): {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${alien.toca.reap-interval}", initialDelayString = "${alien.toca.reap-interval}")
    public void reapExpired() {
        try {
            this.reap.reapExpired();
        } catch (RuntimeException e) {
            log.warn("Falha ao descartar Tocas vencidas: {}", e.getMessage());
        }
    }
}
