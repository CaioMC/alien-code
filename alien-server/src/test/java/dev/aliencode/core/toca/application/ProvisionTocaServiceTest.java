package dev.aliencode.core.toca.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.aliencode.core.toca.application.TocaTestDoubles.FakeHarness;
import dev.aliencode.core.toca.application.TocaTestDoubles.FakeSandbox;
import dev.aliencode.core.toca.application.TocaTestDoubles.FakeSnapshots;
import dev.aliencode.core.toca.application.TocaTestDoubles.InMemoryTocas;
import dev.aliencode.core.toca.domain.exception.TocaProvisioningException;
import dev.aliencode.core.toca.domain.model.RepositorySeed;
import dev.aliencode.core.toca.domain.model.Seed;
import dev.aliencode.core.toca.domain.model.Toca;
import dev.aliencode.core.toca.domain.model.TocaStatus;
import dev.aliencode.core.toca.port.sandbox.ExecResult;
import dev.aliencode.core.toca.port.sandbox.SandboxRequest;
import dev.aliencode.core.toca.usecase.command.ProvisionTocaCommand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProvisionTocaServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");
    private static final String AGENT_CONFIG = "{\"model\":\"ollama/qwen3:8b\"}";

    @TempDir
    Path root;

    private FakeSandbox sandbox;
    private FakeSnapshots snapshots;
    private FakeHarness harness;
    private InMemoryTocas tocas;
    private ProvisionTocaService service;

    @BeforeEach
    void setUp() {
        this.sandbox = new FakeSandbox();
        this.snapshots = new FakeSnapshots();
        this.harness = new FakeHarness();
        this.tocas = new InMemoryTocas();

        TocaSettings settings = new TocaSettings(
                "alien/toca:0.1",
                "alien-net",
                4096,
                "opencode",
                6L << 30,
                4_000_000_000L,
                512,
                Duration.ofMinutes(60),
                Duration.ofSeconds(5),
                List.of(this.root),
                AGENT_CONFIG
        );

        this.service = new ProvisionTocaService(
                this.sandbox,
                this.snapshots,
                this.harness,
                this.tocas,
                settings,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void semeiaCadaRepositorioEEsperaOOpencode() throws IOException {
        Seed seed = new Seed.ExistingRepositories(List.of(this.repo("api"), this.repo("web")));

        Toca toca = this.service.provision(new ProvisionTocaCommand("m-42", seed));

        assertThat(toca.status()).isEqualTo(TocaStatus.READY);
        assertThat(toca.workspaceDirs()).containsExactly("/workspace/api", "/workspace/web");
        assertThat(toca.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(60)));
        assertThat(this.sandbox.copiedTo).containsExactly("/workspace/api", "/workspace/web");
        assertThat(this.snapshots.discarded).hasSize(2);
        assertThat(this.harness.lastEndpoint.baseUrl().toString()).isEqualTo("http://127.0.0.1:40001");
        assertThat(this.tocas.findById(toca.id())).contains(toca);
    }

    @Test
    void criaOContainerComLimitesSenhaERotulos() throws IOException {
        this.service.provision(new ProvisionTocaCommand("m-42", new Seed.ExistingRepositories(List.of(this.repo("api")))));

        SandboxRequest request = this.sandbox.created.getFirst();
        assertThat(request.image()).isEqualTo("alien/toca:0.1");
        assertThat(request.memoryBytes()).isEqualTo(6L << 30);
        assertThat(request.nanoCpus()).isEqualTo(4_000_000_000L);
        assertThat(request.env().get("OPENCODE_SERVER_PASSWORD")).hasSizeGreaterThanOrEqualTo(32);
        assertThat(request.env().get("OPENCODE_SERVER_PASSWORD")).isEqualTo(this.harness.lastEndpoint.password());
        assertThat(request.labels()).containsEntry("alien.mission", "m-42").containsKey("alien.expires-at");
        assertThat(request.env()).containsEntry("OPENCODE_CONFIG_CONTENT", AGENT_CONFIG);
    }

    @Test
    void projetoNovoFazGitInitDentroDaToca() {
        Toca toca = this.service.provision(new ProvisionTocaCommand(null, new Seed.NewProject("demo")));

        assertThat(this.sandbox.executed).containsExactly(List.of("git", "init", "-q", "/workspace/demo"));
        assertThat(toca.workspaceDirs()).containsExactly("/workspace/demo");
    }

    @Test
    void falhaDoOpencodeRemoveOContainerEMarcaFailed() throws IOException {
        this.harness.ready = false;
        Seed seed = new Seed.ExistingRepositories(List.of(this.repo("api")));

        assertThatThrownBy(() -> this.service.provision(new ProvisionTocaCommand(null, seed)))
                .isInstanceOfSatisfying(TocaProvisioningException.class, e -> {
                    assertThat(e.toca().status()).isEqualTo(TocaStatus.FAILED);
                    assertThat(e.toca().failureReason()).contains("opencode");
                });
        assertThat(this.sandbox.removed).hasSize(1);
        assertThat(this.tocas.findAll()).singleElement()
                .satisfies(t -> assertThat(t.status()).isEqualTo(TocaStatus.FAILED));
    }

    @Test
    void falhaNaCopiaAindaDescartaOSnapshot() throws IOException {
        this.sandbox.failOnCopy = new IllegalStateException("disco cheio");

        Seed seed = new Seed.ExistingRepositories(List.of(this.repo("api")));

        assertThatThrownBy(() -> this.service.provision(new ProvisionTocaCommand(null, seed)))
                .isInstanceOf(TocaProvisioningException.class);

        assertThat(this.snapshots.discarded).hasSize(1);
        assertThat(this.sandbox.removed).hasSize(1);
    }

    @Test
    void gitInitComErroFalhaAToca() {
        this.sandbox.execResult = new ExecResult(128, "", "fatal: permissão negada");

        assertThatThrownBy(() -> this.service.provision(new ProvisionTocaCommand(null, new Seed.NewProject("demo"))))
                .hasMessageContaining("permissão negada");
        assertThat(this.sandbox.removed).hasSize(1);
    }

    @Test
    void recusaRepositorioForaDasPastasPermitidasSemCriarContainer() {
        Seed seed = new Seed.ExistingRepositories(List.of(new RepositorySeed("etc", Path.of("/etc"), null)));

        assertThatThrownBy(() -> this.service.provision(new ProvisionTocaCommand(null, seed)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fora das pastas permitidas");
        assertThat(this.sandbox.created).isEmpty();
        assertThat(this.tocas.findAll()).isEmpty();
    }

    private RepositorySeed repo(String name) throws IOException {
        return new RepositorySeed(name, Files.createDirectories(this.root.resolve(name)), null);
    }
}
