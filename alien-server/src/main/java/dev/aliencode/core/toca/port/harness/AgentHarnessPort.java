package dev.aliencode.core.toca.port.harness;

import java.time.Duration;

import dev.aliencode.core.toca.domain.model.TocaEndpoint;

/** O harness do agente que roda dentro da Toca (opencode). */
public interface AgentHarnessPort {

    /**
     * Bloqueia até o harness responder saudável.
     *
     * @return a versão reportada pelo harness
     * @throws HarnessNotReadyException se não responder dentro do tempo
     */
    String awaitReady(TocaEndpoint endpoint, Duration timeout);
}
