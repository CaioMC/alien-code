package dev.aliencode.adapters.toca.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "alien")
public record AlienProperties(Toca toca, Workspace workspace) {

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
            Duration reapInterval) {
    }

    /** @param allowedRoots pastas de onde a API aceita semear repositórios */
    public record Workspace(List<Path> allowedRoots) {
    }
}
