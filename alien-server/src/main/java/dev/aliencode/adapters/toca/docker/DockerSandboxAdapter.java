package dev.aliencode.adapters.toca.docker;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.StreamType;

import dev.aliencode.core.toca.port.sandbox.ExecResult;
import dev.aliencode.core.toca.port.sandbox.ManagedSandbox;
import dev.aliencode.core.toca.port.sandbox.SandboxHandle;
import dev.aliencode.core.toca.port.sandbox.SandboxPort;
import dev.aliencode.core.toca.port.sandbox.SandboxRequest;

/**
 * Tocas como containers Docker. Cada container nasce com:
 * limites de CPU/memória/processos, todas as capabilities removidas, no-new-privileges,
 * a porta do opencode publicada só em 127.0.0.1 (porta aleatória) e rótulos
 * que permitem achar órfãos depois de um reinício.
 */
@Component
public class DockerSandboxAdapter implements SandboxPort {

    static final String LABEL_TOCA = "alien.toca";
    private static final String LOOPBACK = "127.0.0.1";
    private static final int TOCA_UID = 1000;
    private static final int TOCA_GID = 1000;

    private static final Logger log = LoggerFactory.getLogger(DockerSandboxAdapter.class);

    private final DockerClient docker;

    public DockerSandboxAdapter(DockerClient docker) {
        this.docker = docker;
    }

    @Override
    public SandboxHandle create(SandboxRequest request) {
        this.requireImage(request.image());
        this.ensureNetwork(request.network());

        ExposedPort agentPort = ExposedPort.tcp(request.agentPort());
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withNetworkMode(request.network())
                .withPortBindings(new PortBinding(Ports.Binding.bindIp(LOOPBACK), agentPort))
                .withCapDrop(Capability.ALL)
                .withSecurityOpts(List.of("no-new-privileges"))
                .withInit(true);
        if (request.memoryBytes() > 0) {
            hostConfig.withMemory(request.memoryBytes());
        }
        if (request.nanoCpus() > 0) {
            hostConfig.withNanoCPUs(request.nanoCpus());
        }
        if (request.pidsLimit() > 0) {
            hostConfig.withPidsLimit(request.pidsLimit());
        }

        Map<String, String> labels = new HashMap<>(request.labels());
        labels.put(LABEL_TOCA, request.tocaId().value());

        String containerId = this.docker.createContainerCmd(request.image())
                .withName(request.tocaId().value())
                .withEnv(request.env().entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList())
                .withLabels(labels)
                .withExposedPorts(agentPort)
                .withHostConfig(hostConfig)
                .exec()
                .getId();
        try {
            this.docker.startContainerCmd(containerId).exec();
            int hostPort = this.publishedPort(containerId, agentPort);
            log.debug("Container {} de {} publicado em {}:{}", containerId, request.tocaId(), LOOPBACK, hostPort);
            return new SandboxHandle(containerId, LOOPBACK, hostPort);
        } catch (RuntimeException e) {
            this.remove(containerId);
            throw e;
        }
    }

    @Override
    public void copyDirectory(String containerId, Path source, String targetDir) {
        Path target = Path.of(targetDir);
        if (target.getParent() == null || target.getFileName() == null) {
            throw new IllegalArgumentException("Destino inválido na Toca: " + targetDir);
        }
        Path tar = TarArchiver.archive(source, target.getFileName().toString(), TOCA_UID, TOCA_GID);
        try (InputStream in = Files.newInputStream(tar)) {
            this.docker.copyArchiveToContainerCmd(containerId)
                    .withRemotePath(target.getParent().toString())
                    .withTarInputStream(in)
                    .exec();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao copiar " + source + " para a Toca", e);
        } finally {
            deleteQuietly(tar);
        }
    }

    @Override
    public ExecResult exec(String containerId, List<String> command, Duration timeout) {
        ExecCreateCmdResponse created = this.docker.execCreateCmd(containerId)
                .withCmd(command.toArray(String[]::new))
                .withAttachStdout(true)
                .withAttachStderr(true)
                .exec();

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        try (ResultCallback.Adapter<Frame> callback = new ResultCallback.Adapter<>() {
            @Override
            public void onNext(Frame frame) {
                String chunk = new String(frame.getPayload(), StandardCharsets.UTF_8);
                (frame.getStreamType() == StreamType.STDERR ? stderr : stdout).append(chunk);
            }
        }) {
            this.docker.execStartCmd(created.getId()).exec(callback);
            if (!callback.awaitCompletion(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Comando excedeu " + timeout + " na Toca: " + String.join(" ", command));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrompido executando comando na Toca", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        Long exitCode = this.docker.inspectExecCmd(created.getId()).exec().getExitCodeLong();
        return new ExecResult(exitCode == null ? -1 : exitCode.intValue(), stdout.toString(), stderr.toString());
    }

    @Override
    public void remove(String containerId) {
        try {
            this.docker.removeContainerCmd(containerId).withForce(true).withRemoveVolumes(true).exec();
        } catch (NotFoundException e) {
            log.debug("Container {} já não existia", containerId);
        }
    }

    @Override
    public List<ManagedSandbox> listManaged() {
        List<ManagedSandbox> managed = new ArrayList<>();
        for (Container container : this.docker.listContainersCmd()
                .withShowAll(true)
                .withLabelFilter(List.of(LABEL_TOCA))
                .exec()) {
            managed.add(new ManagedSandbox(container.getId(), container.getLabels().get(LABEL_TOCA)));
        }
        return managed;
    }

    private void requireImage(String image) {
        try {
            this.docker.inspectImageCmd(image).exec();
        } catch (NotFoundException e) {
            throw new IllegalStateException("Imagem da Toca não encontrada: " + image
                    + ". Construa com: docker build -t " + image + " toca/", e);
        }
    }

    private void ensureNetwork(String network) {
        boolean exists = this.docker.listNetworksCmd().withNameFilter(network).exec().stream()
                .anyMatch(n -> network.equals(n.getName()));
        if (!exists) {
            this.docker.createNetworkCmd()
                    .withName(network)
                    .withDriver("bridge")
                    .withLabels(Map.of("alien.managed", "true"))
                    .exec();
            log.info("Rede Docker {} criada", network);
        }
    }

    private int publishedPort(String containerId, ExposedPort port) {
        for (int attempt = 0; attempt < 20; attempt++) {
            InspectContainerResponse inspect = this.docker.inspectContainerCmd(containerId).exec();
            Ports.Binding[] bindings = inspect.getNetworkSettings().getPorts().getBindings().get(port);
            if (bindings != null && bindings.length > 0 && bindings[0].getHostPortSpec() != null) {
                return Integer.parseInt(bindings[0].getHostPortSpec());
            }
            if (Boolean.FALSE.equals(inspect.getState().getRunning())) {
                throw new IllegalStateException("O container da Toca parou logo ao iniciar (exit "
                        + inspect.getState().getExitCodeLong() + ")");
            }
            sleep(Duration.ofMillis(100));
        }
        throw new IllegalStateException("O Docker não publicou a porta " + port + " do container " + containerId);
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrompido", e);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Não foi possível apagar {}", path, e);
        }
    }
}
