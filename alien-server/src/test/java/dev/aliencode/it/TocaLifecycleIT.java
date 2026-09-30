package dev.aliencode.it;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;

import dev.aliencode.adapters.toca.web.response.TocaResponse;
import dev.aliencode.core.toca.domain.model.TocaId;
import dev.aliencode.core.toca.port.sandbox.ExecResult;
import dev.aliencode.core.toca.port.sandbox.SandboxPort;
import dev.aliencode.core.toca.port.sandbox.SandboxRequest;
import dev.aliencode.core.toca.usecase.ReapTocasUseCase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Critério de pronto do M0: o Alien Server cria uma Toca real (imagem alien/toca),
 * semeia o workspace, o opencode responde, e a Toca é destruída sem sobras.
 * Precisa de Docker e da imagem: {@code docker build -t alien/toca:0.1 toca/}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TocaLifecycleIT {

    private static final String IMAGE = "alien/toca:0.1";
    private static Path root;

    @Autowired
    TestRestTemplate rest;
    @Autowired
    SandboxPort sandbox;
    @Autowired
    DockerClient docker;
    @Autowired
    ReapTocasUseCase reap;

    private final List<String> created = new java.util.ArrayList<>();

    @BeforeAll
    static void requireDockerAndImage() throws Exception {
        Process process = new ProcessBuilder("docker", "image", "inspect", IMAGE).redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        assumeTrue(process.waitFor() == 0, "Docker ou a imagem " + IMAGE + " indisponível");
        root = Files.createTempDirectory("alien-it-");
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("alien.workspace.allowed-roots", () -> root.toString());
        registry.add("alien.toca.memory", () -> "1GB");
        registry.add("alien.toca.cpus", () -> "1");
        registry.add("alien.mission.store-path", () -> root.resolve("alien.db").toString());
    }

    @AfterEach
    void cleanUp() {
        this.created.forEach(id -> this.rest.delete("/api/tocas/" + id));
    }

    @Test
    void criaSemeiaIsolaEDestroiUmaTocaComRepositorioExistente() throws Exception {
        Path repo = gitRepository("demo");

        Map<String, Object> request = Map.of(
                "missionId", "m-it",
                "seed", Map.of("type", "existing", "repositories", List.of(Map.of("path", repo.toString())))
        );

        ResponseEntity<TocaResponse> response = this.rest.postForEntity("/api/tocas", request, TocaResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        TocaResponse toca = response.getBody();

        this.created.add(toca.id());

        assertThat(toca.status()).isEqualTo("READY");
        assertThat(toca.workspace()).containsExactly("/workspace/demo");

        String containerId = this.docker.inspectContainerCmd(toca.id()).exec().getId();

        // semeadura: conteúdo, dono (usuário alien), bit de execução e git sem remote
        assertThat(this.exec(containerId, "cat", "/workspace/demo/README.md").stdout()).isEqualTo("olá, Toca");
        assertThat(this.exec(containerId, "stat", "-c", "%u", "/workspace/demo/README.md").stdout().strip()).isEqualTo("1000");
        assertThat(this.exec(containerId, "test", "-x", "/workspace/demo/build.sh").succeeded()).isTrue();
        assertThat(this.exec(containerId, "git", "-C", "/workspace/demo", "log", "--format=%s").stdout().strip())
                .isEqualTo("primeiro commit");
        assertThat(this.exec(containerId, "git", "-C", "/workspace/demo", "remote").stdout()).isBlank();

        // isolamento e limites
        InspectContainerResponse inspect = this.docker.inspectContainerCmd(containerId).exec();
        assertThat(inspect.getConfig().getUser()).isEqualTo("alien");
        assertThat(inspect.getHostConfig().getMemory()).isEqualTo(1L << 30);
        assertThat(inspect.getHostConfig().getNanoCPUs()).isEqualTo(1_000_000_000L);
        assertThat(inspect.getHostConfig().getCapDrop()).containsExactly(Capability.ALL);
        assertThat(inspect.getHostConfig().getSecurityOpts()).contains("no-new-privileges");
        assertThat(inspect.getConfig().getLabels()).containsEntry("alien.toca", toca.id())
                .containsEntry("alien.mission", "m-it");
        Ports.Binding binding = inspect.getHostConfig().getPortBindings().getBindings().get(ExposedPort.tcp(4096))[0];
        assertThat(binding.getHostIp()).isEqualTo("127.0.0.1");

        // o opencode está no ar e exige a senha da Toca
        HttpResponse<String> semSenha = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(toca.agentUrl() + "/global/health")).build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(semSenha.statusCode()).isEqualTo(401);

        // descarte
        this.rest.delete("/api/tocas/" + toca.id());

        assertThat(this.rest.getForObject("/api/tocas/" + toca.id(), TocaResponse.class).status()).isEqualTo("DISPOSED");
        assertThatThrownBy(() -> this.docker.inspectContainerCmd(containerId).exec()).isInstanceOf(NotFoundException.class);
    }

    @Test
    void criaProjetoNovo() {
        TocaResponse toca = this.rest.postForObject(
                "/api/tocas",
                Map.of("seed", Map.of("type", "new", "name", "novo")),
                TocaResponse.class
        );

        this.created.add(toca.id());

        assertThat(toca.status()).isEqualTo("READY");

        String containerId = this.docker.inspectContainerCmd(toca.id()).exec().getId();

        assertThat(this.exec(containerId, "test", "-d", "/workspace/novo/.git").succeeded()).isTrue();
    }

    @Test
    void recusaRepositorioForaDasPastasPermitidas() {
        ResponseEntity<String> response = this.rest.postForEntity(
                "/api/tocas",
                Map.of("seed", Map.of("type", "existing", "repositories", List.of(Map.of("path", "/etc")))),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void faxineiroRemoveContainerOrfao() {
        TocaId orphan = TocaId.newId();
        SandboxRequest request = new SandboxRequest(
                orphan,
                IMAGE,
                "alien-net",
                4096,
                0,
                0,
                0,
                Map.of("OPENCODE_SERVER_PASSWORD", "x"),
                Map.of()
        );

        String containerId = this.sandbox.create(request).containerId();

        assertThat(this.reap.removeOrphans()).contains(containerId);

        assertThatThrownBy(() -> this.docker.inspectContainerCmd(containerId).exec()).isInstanceOf(NotFoundException.class);
    }

    private ExecResult exec(String containerId, String... command) {
        return this.sandbox.exec(containerId, List.of(command), Duration.ofSeconds(20));
    }

    private static Path gitRepository(String name) throws IOException, InterruptedException {
        Path repo = Files.createDirectories(root.resolve(name));

        Files.writeString(repo.resolve("README.md"), "olá, Toca");

        Path script = Files.writeString(repo.resolve("build.sh"), "#!/bin/sh\necho ok\n");

        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));

        run(repo, "git", "init", "-q", "-b", "main");
        run(repo, "git", "add", ".");
        run(repo, "git", "-c", "user.name=it", "-c", "user.email=it@it", "commit", "-q", "-m", "primeiro commit");

        return repo;
    }

    private static void run(Path dir, String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor()).as(output).isZero();
    }
}
