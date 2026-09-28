package dev.aliencode.core.toca.port;

/**
 * Container criado e em execução.
 *
 * @param agentHost host (na máquina do dev) onde a porta do agente foi publicada
 * @param agentPort porta publicada no host para o opencode da Toca
 */
public record SandboxHandle(String containerId, String agentHost, int agentPort) {
}
