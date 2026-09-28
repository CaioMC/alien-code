package dev.aliencode.adapters.toca.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;

import dev.aliencode.core.toca.application.TocaSettings;

/**
 * Liga a configuração ao core: o core recebe um {@link TocaSettings} simples,
 * sem saber que ele veio de um application.yml.
 */
@Configuration
public class TocaConfig {

    @Bean
    TocaSettings tocaSettings(AlienProperties properties) {
        AlienProperties.Toca toca = properties.toca();
        return new TocaSettings(
                toca.image(),
                toca.network(),
                toca.agentPort(),
                toca.agentUsername(),
                toca.memory() == null ? 0 : toca.memory().toBytes(),
                Math.round(toca.cpus() * 1_000_000_000L),
                toca.pidsLimit(),
                toca.ttl(),
                toca.readyTimeout(),
                properties.workspace().allowedRoots());
    }

    /** Usa DOCKER_HOST se definido; senão, o socket padrão (unix:///var/run/docker.sock). */
    @Bean(destroyMethod = "close")
    DockerClient dockerClient() {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        ApacheDockerHttpClient http = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .build();
        return DockerClientImpl.getInstance(config, http);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
