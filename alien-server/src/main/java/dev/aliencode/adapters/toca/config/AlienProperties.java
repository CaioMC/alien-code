package dev.aliencode.adapters.toca.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "alien")
public record AlienProperties(
        Toca toca,
        Workspace workspace,
        Agent agent
) {

    public record Toca(
            String image,
            String network,
            int agentPort,
            String agentUsername,
            DataSize memory,
            double cpus,
            long pidsLimit,
            Duration ttl,
            Duration readyTimeout,
            Duration reapInterval
    ) {
    }

    /**
     * O agente (opencode) dentro da Toca e o provedor de modelos que ele usa.
     *
     * @param baseUrl        Ollama visto de dentro da Toca (rede alien-net), com /v1
     * @param models         modelos oferecidos ao agente
     * @param defaultModel   modelo padrão das missões, no formato ollama/modelo
     * @param requestTimeout tempo máximo de uma chamada ao modelo
     */
    public record Agent(
            String baseUrl,
            List<String> models,
            String defaultModel,
            Duration requestTimeout
    ) {
    }

    /** @param allowedRoots pastas de onde a API aceita semear repositórios */
    public record Workspace(List<Path> allowedRoots) {
    }
}
