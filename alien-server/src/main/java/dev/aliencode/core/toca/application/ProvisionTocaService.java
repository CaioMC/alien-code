package dev.aliencode.core.toca.application;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import dev.aliencode.core.toca.domain.exception.TocaProvisioningException;
import dev.aliencode.core.toca.domain.model.RepositorySeed;
import dev.aliencode.core.toca.domain.model.Seed;
import dev.aliencode.core.toca.domain.model.Toca;
import dev.aliencode.core.toca.domain.model.TocaEndpoint;
import dev.aliencode.core.toca.domain.model.TocaId;
import dev.aliencode.core.toca.port.harness.AgentHarnessPort;
import dev.aliencode.core.toca.port.repository.TocaRepository;
import dev.aliencode.core.toca.port.sandbox.ExecResult;
import dev.aliencode.core.toca.port.sandbox.SandboxHandle;
import dev.aliencode.core.toca.port.sandbox.SandboxPort;
import dev.aliencode.core.toca.port.sandbox.SandboxRequest;
import dev.aliencode.core.toca.port.workspace.WorkspaceSnapshotPort;
import dev.aliencode.core.toca.usecase.ProvisionTocaUseCase;
import dev.aliencode.core.toca.usecase.command.ProvisionTocaCommand;

/**
 * Passos 1 a 3 do ciclo de vida da Toca (especificação, seção 5):
 * <ol>
 *   <li><b>provisionar</b> o container com limites e senha própria;</li>
 *   <li><b>semear</b> /workspace com cópias dos repositórios (ou um projeto novo);</li>
 *   <li><b>preparar</b>: esperar o opencode responder.</li>
 * </ol>
 * Se qualquer passo falhar, o container é removido na hora e a Toca fica FAILED.
 */
@Service
public class ProvisionTocaService implements ProvisionTocaUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProvisionTocaService.class);
    private static final String WORKSPACE = "/workspace";
    private static final Duration GIT_INIT_TIMEOUT = Duration.ofSeconds(30);

    private final SandboxPort sandbox;
    private final WorkspaceSnapshotPort snapshots;
    private final AgentHarnessPort harness;
    private final TocaRepository tocas;
    private final TocaSettings settings;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public ProvisionTocaService(SandboxPort sandbox, WorkspaceSnapshotPort snapshots, AgentHarnessPort harness,
                                TocaRepository tocas, TocaSettings settings, Clock clock) {
        this.sandbox = sandbox;
        this.snapshots = snapshots;
        this.harness = harness;
        this.tocas = tocas;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    public Toca provision(ProvisionTocaCommand command) {
        this.validateSeed(command.seed());

        Instant now = this.clock.instant();
        Toca toca = Toca.provisioning(TocaId.newId(), command.missionId(), now, now.plus(this.settings.ttl()));
        this.tocas.save(toca);
        log.info("Provisionando {} (missão {})", toca.id(), command.missionId());

        try {
            String password = this.newPassword();
            SandboxHandle handle = this.sandbox.create(this.sandboxRequest(toca, password));
            TocaEndpoint endpoint = new TocaEndpoint(
                    URI.create("http://" + handle.agentHost() + ":" + handle.agentPort()),
                    this.settings.agentUsername(), password);
            toca = toca.withContainer(handle.containerId(), endpoint);
            this.tocas.save(toca);

            List<String> dirs = this.seed(toca, command.seed());
            String version = this.harness.awaitReady(endpoint, this.settings.readyTimeout());

            toca = toca.ready(dirs);
            this.tocas.save(toca);
            log.info("{} pronta: container {}, opencode {}, workspace {}", toca.id(), toca.containerId(), version, dirs);
            return toca;
        } catch (RuntimeException e) {
            Toca failed = toca.failed(e.getMessage());
            if (failed.hasContainer()) {
                this.removeQuietly(failed.containerId());
            }
            this.tocas.save(failed);
            log.warn("Falha ao provisionar {}: {}", failed.id(), e.getMessage());
            throw new TocaProvisioningException(failed, e);
        }
    }

    private void validateSeed(Seed seed) {
        if (seed instanceof Seed.ExistingRepositories existing) {
            for (RepositorySeed repository : existing.repositories()) {
                if (!this.settings.isAllowed(repository.path())) {
                    throw new IllegalArgumentException("O repositório " + repository.path()
                            + " está fora das pastas permitidas (alien.workspace.allowed-roots)");
                }
                if (!Files.isDirectory(repository.path())) {
                    throw new IllegalArgumentException("Repositório não encontrado: " + repository.path());
                }
            }
        }
    }

    private SandboxRequest sandboxRequest(Toca toca, String password) {
        Map<String, String> env = new HashMap<>();
        env.put("OPENCODE_SERVER_PASSWORD", password);
        env.put("OPENCODE_SERVER_USERNAME", this.settings.agentUsername());
        env.put("OPENCODE_PORT", String.valueOf(this.settings.agentPort()));

        Map<String, String> labels = new HashMap<>();
        labels.put("alien.expires-at", toca.expiresAt().toString());
        if (toca.missionId() != null) {
            labels.put("alien.mission", toca.missionId());
        }
        return new SandboxRequest(toca.id(), this.settings.image(), this.settings.network(),
                this.settings.agentPort(), this.settings.memoryBytes(), this.settings.nanoCpus(),
                this.settings.pidsLimit(), env, labels);
    }

    private List<String> seed(Toca toca, Seed seed) {
        return switch (seed) {
            case Seed.ExistingRepositories existing -> this.seedRepositories(toca, existing.repositories());
            case Seed.NewProject project -> this.seedNewProject(toca, project.name());
        };
    }

    private List<String> seedRepositories(Toca toca, List<RepositorySeed> repositories) {
        List<String> dirs = new ArrayList<>();
        for (RepositorySeed repository : repositories) {
            Path snapshot = this.snapshots.snapshot(repository);
            try {
                String target = WORKSPACE + "/" + repository.name();
                this.sandbox.copyDirectory(toca.containerId(), snapshot, target);
                dirs.add(target);
            } finally {
                this.snapshots.discard(snapshot);
            }
        }
        return dirs;
    }

    private List<String> seedNewProject(Toca toca, String name) {
        String target = WORKSPACE + "/" + name;
        ExecResult result = this.sandbox.exec(toca.containerId(), List.of("git", "init", "-q", target),
                GIT_INIT_TIMEOUT);
        if (!result.succeeded()) {
            throw new IllegalStateException("git init falhou na Toca: " + result.stderr().strip());
        }
        return List.of(target);
    }

    private void removeQuietly(String containerId) {
        try {
            this.sandbox.remove(containerId);
        } catch (RuntimeException e) {
            log.error("Não foi possível remover o container {}; o faxineiro tenta de novo", containerId, e);
        }
    }

    private String newPassword() {
        byte[] bytes = new byte[24];
        this.random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
