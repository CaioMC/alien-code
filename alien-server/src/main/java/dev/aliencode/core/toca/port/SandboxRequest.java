package dev.aliencode.core.toca.port;

import dev.aliencode.core.toca.domain.TocaId;

import java.util.Map;

/**
 * Tudo que o adaptador de containers precisa para criar uma Toca.
 *
 * @param memoryBytes limite de memória (0 = sem limite)
 * @param nanoCpus    limite de CPU em bilionésimos de CPU (0 = sem limite)
 * @param pidsLimit   máximo de processos (0 = sem limite)
 * @param env         variáveis de ambiente do container
 * @param labels      rótulos extras (os de identificação da Toca são adicionados pelo adaptador)
 */
public record SandboxRequest(
        TocaId tocaId,
        String image,
        String network,
        int agentPort,
        long memoryBytes,
        long nanoCpus,
        long pidsLimit,
        Map<String, String> env,
        Map<String, String> labels) {

    public SandboxRequest {
        env = env == null ? Map.of() : Map.copyOf(env);
        labels = labels == null ? Map.of() : Map.copyOf(labels);
    }
}
