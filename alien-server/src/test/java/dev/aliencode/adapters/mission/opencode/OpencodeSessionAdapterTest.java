package dev.aliencode.adapters.mission.opencode;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import dev.aliencode.core.mission.domain.model.AgentModel;
import dev.aliencode.core.mission.port.agent.AgentEvent;
import dev.aliencode.core.mission.port.agent.AgentSubscription;
import dev.aliencode.core.toca.domain.model.TocaEndpoint;

import static dev.aliencode.adapters.mission.opencode.OpencodeFixtures.COMPLETED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpencodeSessionAdapterTest {

    private static final String AUTH = "Basic " + Base64.getEncoder().encodeToString("opencode:s3nha".getBytes(StandardCharsets.UTF_8));
    private static final String DIRECTORY = "/workspace/demo";

    private final ObjectMapper json = new ObjectMapper();
    private final OpencodeSessionAdapter adapter = new OpencodeSessionAdapter(this.json);

    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final List<JsonNode> bodies = new CopyOnWriteArrayList<>();
    private final CountDownLatch streamClosed = new CountDownLatch(1);

    private HttpServer server;
    private volatile boolean holdStreamOpen;

    @BeforeEach
    void startFakeOpencode() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.createContext("/", this::handle);
        this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        this.server.start();
    }

    @AfterEach
    void stop() {
        this.server.stop(0);
    }

    @Test
    void recebeOFluxoDeEventosTraduzidoAteOIdle() throws InterruptedException {
        List<AgentEvent> received = new CopyOnWriteArrayList<>();
        CountDownLatch idle = new CountDownLatch(1);

        try (AgentSubscription subscription = this.adapter.subscribe(this.endpoint(), DIRECTORY, event -> {
            received.add(event);

            if (event instanceof AgentEvent.SessionIdle) {
                idle.countDown();
            }
        })) {
            assertThat(idle.await(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(received).filteredOn(AgentEvent.ToolFinished.class::isInstance).hasSize(3);
        assertThat(this.requests).contains("GET /event?directory=/workspace/demo");
    }

    @Test
    void fecharAAssinaturaEncerraOFluxo() throws InterruptedException {
        this.holdStreamOpen = true;

        AgentSubscription subscription = this.adapter.subscribe(this.endpoint(), DIRECTORY, event -> {
        });

        subscription.close();

        assertThat(this.streamClosed.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void criaSessaoEnviaPromptEAbortaNoDiretorioDaToca() {
        String sessionId = this.adapter.createSession(this.endpoint(), DIRECTORY, "Corrija a soma");

        this.adapter.prompt(
                this.endpoint(),
                DIRECTORY,
                sessionId,
                AgentModel.parse("ollama/qwen3:8b"),
                "Corrija a soma em calc.py"
        );
        this.adapter.abort(this.endpoint(), DIRECTORY, sessionId);

        assertThat(sessionId).isEqualTo("ses_fake");
        assertThat(this.requests).containsExactly(
                "POST /session?directory=/workspace/demo",
                "POST /session/ses_fake/prompt_async?directory=/workspace/demo",
                "POST /session/ses_fake/abort?directory=/workspace/demo"
        );

        assertThat(this.bodies.getFirst().path("title").asText()).isEqualTo("Corrija a soma");

        JsonNode prompt = this.bodies.get(1);

        assertThat(prompt.path("model").path("providerID").asText()).isEqualTo("ollama");
        assertThat(prompt.path("model").path("modelID").asText()).isEqualTo("qwen3:8b");
        assertThat(prompt.path("parts").get(0).path("text").asText()).isEqualTo("Corrija a soma em calc.py");
    }

    @Test
    void senhaErradaViraErroComOStatus() {
        TocaEndpoint wrong = new TocaEndpoint(this.endpoint().baseUrl(), "opencode", "errada");

        assertThatThrownBy(() -> this.adapter.createSession(wrong, DIRECTORY, "x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 401");

        assertThatThrownBy(() -> this.adapter.subscribe(wrong, DIRECTORY, event -> {
        })).hasMessageContaining("HTTP 401");
    }

    private TocaEndpoint endpoint() {
        return new TocaEndpoint(URI.create("http://127.0.0.1:" + this.server.getAddress().getPort()), "opencode", "s3nha");
    }

    private void handle(HttpExchange exchange) throws IOException {
        if (!AUTH.equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();

            return;
        }

        URI uri = exchange.getRequestURI();

        this.requests.add(exchange.getRequestMethod() + " " + uri.getPath() + "?" + uri.getQuery());

        if ("GET".equals(exchange.getRequestMethod())) {
            this.stream(exchange);
            return;
        }

        this.bodies.add(this.json.readTree(exchange.getRequestBody()));

        if (uri.getPath().equals("/session")) {
            this.respond(exchange, 200, this.json.writeValueAsString(Map.of("id", "ses_fake")));
        } else if (uri.getPath().endsWith("/abort")) {
            this.respond(exchange, 200, "true");
        } else {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        }
    }

    private void stream(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);

        try (OutputStream out = exchange.getResponseBody()) {
            out.write(OpencodeFixtures.raw(COMPLETED).getBytes(StandardCharsets.UTF_8));
            out.flush();

            while (this.holdStreamOpen) {
                out.write(": ping\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
                Thread.sleep(50);
            }
        } catch (IOException e) {
            this.streamClosed.countDown();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void respond(
            HttpExchange exchange,
            int status,
            String body
    ) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);

        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
