package dev.aliencode.adapters.mission.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import dev.aliencode.adapters.toca.config.AlienProperties;
import dev.aliencode.core.mission.application.MissionSettings;
import dev.aliencode.core.mission.domain.model.AgentModel;

@Configuration
public class MissionConfig {

    @Bean
    MissionSettings missionSettings(AlienProperties alien, MissionProperties mission) {
        return new MissionSettings(
                AgentModel.parse(alien.agent().defaultModel()),
                mission.taskTimeout(),
                mission.keepToca()
        );
    }

    /** Uma missão = um thread virtual, que passa a maior parte do tempo esperando o agente. */
    @Bean(name = "missionExecutor", destroyMethod = "shutdownNow")
    ExecutorService missionExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("mission-", 0).factory());
    }

    /**
     * SQLite em arquivo local, em modo WAL: leituras (replay) não bloqueiam a gravação de eventos.
     * O esquema é criado pelo schema.sql na subida.
     */
    @Bean
    DataSource dataSource(MissionProperties mission) {
        Path file = mission.storePath().toAbsolutePath();

        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível criar a pasta do banco " + file.getParent(), e);
        }

        SQLiteConfig config = new SQLiteConfig();

        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setBusyTimeout(5_000);

        SQLiteDataSource dataSource = new SQLiteDataSource(config);

        dataSource.setUrl("jdbc:sqlite:" + file);

        return dataSource;
    }
}
