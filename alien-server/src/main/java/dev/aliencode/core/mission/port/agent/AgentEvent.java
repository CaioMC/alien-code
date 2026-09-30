package dev.aliencode.core.mission.port.agent;

import java.util.Map;

/**
 * O que o harness do agente reporta durante uma sessão, já sem o formato do opencode.
 * O adaptador traduz o fluxo SSE para estes tipos; trocar de harness é trocar o adaptador.
 */
public sealed interface AgentEvent {

    String sessionId();

    /** Trecho de texto da resposta do agente. */
    record TextDelta(
            String sessionId,
            String partId,
            String text
    ) implements AgentEvent {
    }

    /** Trecho do raciocínio do modelo. */
    record ReasoningDelta(
            String sessionId,
            String partId,
            String text
    ) implements AgentEvent {
    }

    /** O agente começou a executar uma ferramenta (read, edit, bash, MCP...). */
    record ToolStarted(
            String sessionId,
            String callId,
            String tool,
            String title,
            Map<String, Object> input
    ) implements AgentEvent {
    }

    /**
     * A ferramenta terminou.
     *
     * @param exitCode código de saída, quando a ferramenta é um comando (bash); nulo nas demais
     */
    record ToolFinished(
            String sessionId,
            String callId,
            String tool,
            boolean success,
            String title,
            String output,
            Integer exitCode,
            Long durationMs
    ) implements AgentEvent {
    }

    /** Um arquivo do workspace foi alterado por uma ferramenta de edição. */
    record FileChanged(
            String sessionId,
            String path,
            String patch,
            int additions,
            int deletions
    ) implements AgentEvent {
    }

    /** Uma chamada ao modelo terminou; traz o consumo de tokens dela. */
    record ModelCallFinished(
            String sessionId,
            long inputTokens,
            long outputTokens,
            long reasoningTokens
    ) implements AgentEvent {
    }

    /** A sessão parou de trabalhar: terminou a tarefa, foi abortada ou falhou. */
    record SessionIdle(String sessionId) implements AgentEvent {
    }

    /**
     * A sessão falhou.
     *
     * @param aborted {@code true} quando o erro é consequência de um pedido de abort
     */
    record SessionFailed(
            String sessionId,
            String message,
            boolean aborted
    ) implements AgentEvent {
    }
}
