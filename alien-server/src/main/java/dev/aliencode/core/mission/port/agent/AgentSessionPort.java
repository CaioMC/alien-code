package dev.aliencode.core.mission.port.agent;

import java.util.function.Consumer;

import dev.aliencode.core.mission.domain.model.AgentModel;
import dev.aliencode.core.toca.domain.model.TocaEndpoint;

/**
 * Sessões de trabalho do agente que roda na Toca (opencode serve).
 * Todas as operações recebem o diretório do workspace: é ele que define o projeto no opencode.
 */
public interface AgentSessionPort {

    /**
     * Passa a receber os eventos do agente para o diretório. Deve ser chamado antes de
     * {@link #prompt}, para não perder o começo da execução.
     */
    AgentSubscription subscribe(
            TocaEndpoint endpoint,
            String directory,
            Consumer<AgentEvent> listener
    );

    /** @return o id da sessão criada */
    String createSession(
            TocaEndpoint endpoint,
            String directory,
            String title
    );

    /** Envia a instrução sem bloquear; o progresso chega pelos eventos. */
    void prompt(
            TocaEndpoint endpoint,
            String directory,
            String sessionId,
            AgentModel model,
            String text
    );

    /** Interrompe a sessão. A sessão termina com {@link AgentEvent.SessionFailed} abortado. */
    void abort(
            TocaEndpoint endpoint,
            String directory,
            String sessionId
    );
}
