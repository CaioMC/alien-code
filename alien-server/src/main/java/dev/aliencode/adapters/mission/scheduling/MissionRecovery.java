package dev.aliencode.adapters.mission.scheduling;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import dev.aliencode.core.mission.usecase.FailInterruptedMissionsUseCase;

/** Na subida, missões que ficaram ativas de uma execução anterior do servidor viram FAILED. */
@Component
public class MissionRecovery {

    private static final Logger log = LoggerFactory.getLogger(MissionRecovery.class);

    private final FailInterruptedMissionsUseCase failInterrupted;

    public MissionRecovery(FailInterruptedMissionsUseCase failInterrupted) {
        this.failInterrupted = failInterrupted;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void failInterruptedOnStartup() {
        List<String> failed = this.failInterrupted.failInterrupted();

        if (!failed.isEmpty()) {
            log.info("{} missão(ões) interrompida(s) pelo reinício marcada(s) como FAILED: {}", failed.size(), failed);
        }
    }
}
