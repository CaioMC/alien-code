package dev.aliencode.adapters.toca.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Name;
import org.springframework.util.unit.DataSize;

import static java.util.Objects.isNull;

@ConfigurationProperties(prefix = "alien")
public record AlienProperties(
        Toca toca,
        Workspace workspace,
        Model model
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
     * O modelo que o agente (opencode) usa dentro da Toca, no mesmo formato do opencode.
     *
     * @param defaultModel   modelo padrão das missões, no formato provedor/modelo ({@code alien.model.default})
     * @param requestTimeout tempo máximo de uma chamada ao modelo
     * @param providers      provedores por id (o id é o prefixo dos modelos)
     */
    public record Model(
            @Name("default") String defaultModel,
            Duration requestTimeout,
            Map<String, Provider> providers
    ) {

        public Model {
            providers = isNull(providers) ? Map.of() : providers;
        }
    }

    /**
     * Provedor compatível com a API da OpenAI.
     *
     * @param name    nome exibido pelo opencode (opcional; padrão = id)
     * @param baseUrl provedor visto de dentro da Toca, com /v1
     * @param apiKey  chave do provedor (vazia para o Ollama local)
     * @param models  modelos oferecidos ao agente
     */
    public record Provider(
            String name,
            String baseUrl,
            String apiKey,
            List<String> models
    ) {

        @Override
        public String toString() {
            return "Provider[name=" + this.name + ", baseUrl=" + this.baseUrl + ", models=" + this.models + "]";
        }
    }

    /** @param allowedRoots pastas de onde a API aceita semear repositórios */
    public record Workspace(List<Path> allowedRoots) {
    }
}
