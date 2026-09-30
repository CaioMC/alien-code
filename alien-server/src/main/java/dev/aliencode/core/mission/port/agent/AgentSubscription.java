package dev.aliencode.core.mission.port.agent;

/** Assinatura do fluxo de eventos do agente. Fechar encerra a conexão. */
public interface AgentSubscription extends AutoCloseable {

    @Override
    void close();
}
