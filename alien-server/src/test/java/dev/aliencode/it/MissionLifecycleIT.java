package dev.aliencode.it;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;

import dev.aliencode.adapters.mission.web.response.MissionResponse;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Critério de pronto do M1: uma missão com 1 repositório e 1 tarefa roda numa Toca real, com o
 * opencode real; o SSE do opencode vira eventos da timeline, entregues ao vivo por WebSocket, com
 * reconexão por lastSeq; o Parar aborta a sessão. O modelo é o {@link ScriptedLlmServer}.
 *
 * <p>Precisa de Docker, da imagem alien/toca:0.1 e da rede alien-net ({@code docker compose up -d}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MissionLifecycleIT {

    private static final String IMAGE = "alien/toca:0.1";
    private static final Duration MISSION_TIMEOUT = Duration.ofSeconds(90);

    private static Path root;
    private static ScriptedLlmServer llm;

    @Autowired
    TestRestTemplate rest;
    @Autowired
    DockerClient docker;

    @LocalServerPort
    int port;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeAll
    static void requireDockerImageAndNetwork() throws Exception {
        assumeTrue(succeeds("docker", "image", "inspect", IMAGE), "Docker ou a imagem " + IMAGE + " indisponível");
        assumeTrue(succeeds("docker", "network", "inspect", "alien-net"), "Rede alien-net ausente: rode docker compose up -d");

        root = Files.createTempDirectory("alien-mission-it-");
        llm = new ScriptedLlmServer(output("docker", "network", "inspect", "alien-net", "-f", "{{(index .IPAM.Config 0).Gateway}}"));
    }

    @AfterAll
    static void stopLlm() {
        if (nonNull(llm)) {
            llm.close();
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("alien.workspace.allowed-roots", () -> root.toString());
        registry.add("alien.toca.memory", () -> "1GB");
        registry.add("alien.toca.cpus", () -> "1");
        registry.add("alien.model.default", () -> "ollama/roteiro");
        registry.add("alien.model.providers.ollama.base-url", () -> llm.baseUrl());
        registry.add("alien.model.providers.ollama.models[0]", () -> "roteiro");
        registry.add("alien.mission.task-timeout", () -> "60s");
        registry.add("alien.mission.store-path", () -> root.resolve("alien.db").toString());
    }

    @Test
    void missaoMostraCadaPassoAoVivoTerminaEDescartaAToca() throws Exception {
        MissionResponse mission = this.start("Corrija a função soma em calc.py");
        TimelineClient timeline = this.connect(mission.id(), 0);

        await().atMost(MISSION_TIMEOUT).until(() -> timeline.has("step.completed", "toca.dispose"));

        assertThat(timeline.types()).containsSubsequence(
                "mission.created",
                "mission.state",
                "step.started",
                "step.completed",
                "step.started",
                "tool.started",
                "tool.completed",
                "tool.started",
                "tool.completed",
                "file.changed",
                "tool.started",
                "tool.completed",
                "terminal.output",
                "assistant.delta",
                "step.completed",
                "mission.state",
                "step.completed"
        );

        assertThat(timeline.states()).containsSubsequence("PROVISIONING", "EXECUTING", "COMPLETED");
        assertThat(timeline.first("terminal.output").path("payload").path("output").asText()).isEqualTo("5\n");
        assertThat(timeline.first("file.changed").path("payload").path("patch").asText()).contains("+    return a + b");
        assertThat(timeline.seqs()).isEqualTo(java.util.stream.LongStream.rangeClosed(1, timeline.size()).boxed().toList());

        MissionResponse snapshot = this.rest.getForObject("/api/missions/" + mission.id(), MissionResponse.class);

        assertThat(snapshot.status()).isEqualTo("COMPLETED");
        assertThat(snapshot.lastSeq()).isEqualTo(timeline.size());
        assertThat(this.tocaContainers(mission.id())).isEmpty();

        // reconexão: quem já viu até N recebe só o que vem depois
        TimelineClient reconnected = this.connect(mission.id(), timeline.size() - 3);

        await().atMost(5, TimeUnit.SECONDS).until(() -> reconnected.size() == 3);
        assertThat(reconnected.seqs()).containsExactly((long) timeline.size() - 2, (long) timeline.size() - 1, (long) timeline.size());

        timeline.close();
        reconnected.close();
    }

    @Test
    void pararPeloWebSocketAbortaASessaoECancelaAMissao() throws Exception {
        MissionResponse mission = this.start("dorme um pouco");
        TimelineClient timeline = this.connect(mission.id(), 0);

        await().atMost(MISSION_TIMEOUT).until(() -> timeline.has("tool.started", null));

        timeline.send("{\"type\":\"stop\"}");

        await().atMost(Duration.ofSeconds(30)).until(() -> timeline.states().contains("CANCELLED"));
        await().atMost(Duration.ofSeconds(30)).until(() -> this.tocaContainers(mission.id()).isEmpty());

        assertThat(timeline.first("tool.completed").path("payload").path("status").asText()).isEqualTo("cancelled");
        assertThat(this.rest.getForObject("/api/missions/" + mission.id(), MissionResponse.class).status()).isEqualTo("CANCELLED");

        timeline.close();
    }

    private MissionResponse start(String prompt) throws IOException, InterruptedException {
        Path repo = calcRepository();
        Map<String, Object> request = Map.of("prompt", prompt, "seed", Map.of("type", "existing", "repositories", List.of(Map.of("path", repo.toString()))));

        return this.rest.postForObject("/api/missions", request, MissionResponse.class);
    }

    private TimelineClient connect(
            String missionId,
            long lastSeq
    ) {
        URI uri = URI.create("ws://127.0.0.1:" + this.port + "/ws/missions/" + missionId + "?lastSeq=" + lastSeq);
        TimelineClient client = new TimelineClient(this.json);

        client.socket = HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(uri, client).join();

        return client;
    }

    private List<Container> tocaContainers(String missionId) {
        return this.docker.listContainersCmd()
                .withShowAll(true)
                .withLabelFilter(Map.of("alien.mission", missionId))
                .exec();
    }

    private static Path calcRepository() throws IOException, InterruptedException {
        Path repo = Files.createDirectories(root.resolve("calc"));

        if (Files.exists(repo.resolve(".git"))) {
            return repo;
        }

        Files.writeString(repo.resolve("calc.py"), "def soma(a, b):\n    return a - b\n");

        run(repo, "git", "init", "-q", "-b", "main");
        run(repo, "git", "add", ".");
        run(repo, "git", "-c", "user.name=it", "-c", "user.email=it@it", "commit", "-q", "-m", "soma com bug");

        return repo;
    }

    private static void run(
            Path dir,
            String... command
    ) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(process.waitFor()).as(output).isZero();
    }

    private static boolean succeeds(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();

        process.getInputStream().readAllBytes();

        return process.waitFor() == 0;
    }

    private static String output(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();

        assertThat(process.waitFor()).as(output).isZero();

        return output;
    }

    /** Cliente WebSocket que guarda cada envelope recebido. */
    private static final class TimelineClient implements WebSocket.Listener {

        private final ObjectMapper json;
        private final List<JsonNode> events = new CopyOnWriteArrayList<>();
        private final StringBuilder partial = new StringBuilder();

        private WebSocket socket;

        TimelineClient(ObjectMapper json) {
            this.json = json;
        }

        @Override
        public CompletionStage<?> onText(
                WebSocket webSocket,
                CharSequence data,
                boolean last
        ) {
            this.partial.append(data);

            if (last) {
                try {
                    this.events.add(this.json.readTree(this.partial.toString()));
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }

                this.partial.setLength(0);
            }

            webSocket.request(1);

            return null;
        }

        void send(String text) {
            this.socket.sendText(text, true).join();
        }

        void close() {
            this.socket.sendClose(WebSocket.NORMAL_CLOSURE, "fim").join();
        }

        int size() {
            return this.events.size();
        }

        List<String> types() {
            return this.events.stream().map(event -> event.path("type").asText()).toList();
        }

        List<Long> seqs() {
            return this.events.stream().map(event -> event.path("seq").asLong()).toList();
        }

        List<String> states() {
            return this.events.stream()
                    .filter(event -> "mission.state".equals(event.path("type").asText()))
                    .map(event -> event.path("payload").path("status").asText())
                    .toList();
        }

        boolean has(
                String type,
                String stepId
        ) {
            return this.events.stream().anyMatch(event -> type.equals(event.path("type").asText()) && (isNull(stepId) || stepId.equals(event.path("stepId").asText())));
        }

        JsonNode first(String type) {
            return this.events.stream().filter(event -> type.equals(event.path("type").asText())).findFirst().orElseThrow();
        }
    }
}
