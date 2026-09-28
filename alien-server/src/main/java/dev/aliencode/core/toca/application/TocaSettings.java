package dev.aliencode.core.toca.application;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Configuração das Tocas, já sem nada de Spring.
 *
 * @param allowedRoots só repositórios dentro destas pastas podem ser semeados (a API aceita caminhos locais)
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
        List<Path> allowedRoots) {

    public TocaSettings {
        if (image == null || image.isBlank()) {
            throw new IllegalArgumentException("Configure a imagem da Toca (alien.toca.image)");
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("O TTL da Toca precisa ser positivo");
        }
        allowedRoots = allowedRoots == null ? List.of()
                : allowedRoots.stream().map(p -> p.toAbsolutePath().normalize()).toList();
    }

    public boolean isAllowed(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        return this.allowedRoots.stream().anyMatch(normalized::startsWith);
    }
}
