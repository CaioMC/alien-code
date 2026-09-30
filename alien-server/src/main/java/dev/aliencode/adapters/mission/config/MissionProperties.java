package dev.aliencode.adapters.mission.config;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param taskTimeout tempo de parede máximo de uma tarefa
 * @param keepToca    modo debug: mantém a Toca ao fim da missão (até o TTL)
 * @param storePath   arquivo SQLite com missões e eventos
 */
@ConfigurationProperties(prefix = "alien.mission")
public record MissionProperties(
        Duration taskTimeout,
        boolean keepToca,
        Path storePath
) {
}
