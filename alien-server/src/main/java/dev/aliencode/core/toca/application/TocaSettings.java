package dev.aliencode.core.toca.application;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static java.util.Objects.isNull;

/**
 * Configuração das Tocas, já sem nada de Spring.
 *
 * @param allowedRoots só repositórios dentro destas pastas podem ser semeados (a API aceita caminhos locais)
 * @param agentConfig  configuração do opencode (JSON) entregue à Toca em OPENCODE_CONFIG_CONTENT; nula = padrão da imagem
 */
public record TocaSettings(
        String image,
        String network,
        int agentPort,
        String agentUsername,
        long memoryBytes,
        long nanoCpus,
        long pidsLimit,
        Duration ttl,
        Duration readyTimeout,
        List<Path> allowedRoots,
        String agentConfig
) {

    public TocaSettings {
        if (isNull(image) || image.isBlank()) {
            throw new IllegalArgumentException("Configure a imagem da Toca (alien.toca.image)");
        }

        if (isNull(ttl) || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("O TTL da Toca precisa ser positivo");
        }

        allowedRoots = isNull(allowedRoots) ?
                List.of() :
                allowedRoots.stream().map(p -> p.toAbsolutePath().normalize()).toList();
    }

    public boolean isAllowed(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        return this.allowedRoots.stream().anyMatch(normalized::startsWith);
    }
}
