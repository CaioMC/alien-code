package dev.aliencode.adapters.mission.opencode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.aliencode.core.mission.domain.model.AgentModel;
import dev.aliencode.core.mission.port.agent.AgentEvent;
import dev.aliencode.core.mission.port.agent.AgentSessionPort;
import dev.aliencode.core.mission.port.agent.AgentSubscription;
import dev.aliencode.core.toca.domain.model.TocaEndpoint;

/**
 * Sessões do opencode pela API HTTP do {@code opencode serve} (contrato em
 * {@code docs/referencias/opencode-1.18.33-openapi.json}), com o SSE lido direto em Java.
 */
@Component
public class OpencodeSessionAdapter implements AgentSessionPort {

    private static final Logger log = LoggerFactory.getLogger(OpencodeSessionAdapter.class);

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /**
     * HTTP/1.1 explícito: com o padrão (HTTP/2 via upgrade h2c), o GET do SSE no servidor do opencode
     * nunca devolve os cabeçalhos e a assinatura trava.
     */
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper json;

    public OpencodeSessionAdapter(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public AgentSubscription subscribe(
            TocaEndpoint endpoint,
            String directory,
            Consumer<AgentEvent> listener
    ) {
        HttpRequest request = this.request(endpoint, "/event", directory)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "text/event-stream")
                .GET()
                .build();

        InputStream stream = this.openStream(request);
        OpencodeEventTranslator translator = new OpencodeEventTranslator(this.json);

        Thread reader = Thread.ofVirtual()
                .name("opencode-sse-" + endpoint.baseUrl().getPort())
                .start(() -> this.read(stream, translator, listener));

        return () -> {
            reader.interrupt();
            closeQuietly(stream);
        };
    }

    @Override
    public String createSession(
            TocaEndpoint endpoint,
            String directory,
            String title
    ) {
        JsonNode session = this.post(endpoint, "/session", directory, Map.of("title", title));

        return session.path("id").asText();
    }

    @Override
    public void prompt(
            TocaEndpoint endpoint,
            String directory,
            String sessionId,
            AgentModel model,
            String text
    ) {
        Map<String, Object> body = Map.of(
                "model", Map.of("providerID", model.providerId(), "modelID", model.modelId()),
                "parts", List.of(Map.of("type", "text", "text", text))
        );

        String path = "/session/" + sessionId + "/prompt_async";

        this.post(
                endpoint,
                path,
                directory,
                body
        );
    }

    @Override
    public void abort(
            TocaEndpoint endpoint,
            String directory,
            String sessionId
    ) {
        this.post(endpoint, "/session/" + sessionId + "/abort", directory, Map.of());
    }

    private void read(
            InputStream stream,
            OpencodeEventTranslator translator,
            Consumer<AgentEvent> listener
    ) {
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder data = new StringBuilder();
            String line;

            while ((line = lines.readLine()) != null) {
                if (line.startsWith("data:")) {
                    data.append(line.substring(5).strip());
                } else if (line.isEmpty() && !data.isEmpty()) {
                    this.dispatch(data.toString(), translator, listener);
                    data.setLength(0);
                }
            }
        } catch (IOException e) {
            log.debug("Fluxo de eventos do opencode encerrado: {}", e.getMessage());
        }
    }

    /** Um evento malformado ou um ouvinte com erro não derrubam o fluxo inteiro. */
    private void dispatch(
            String data,
            OpencodeEventTranslator translator,
            Consumer<AgentEvent> listener
    ) {
        try {
            translator.translate(this.json.readTree(data)).forEach(listener);
        } catch (JsonProcessingException e) {
            log.warn("Evento do opencode ignorado (JSON inválido): {}", e.getOriginalMessage());
        } catch (RuntimeException e) {
            log.warn("Falha tratando evento do opencode: {}", e.getMessage(), e);
        }
    }

    private InputStream openStream(HttpRequest request) {
        try {
            HttpResponse<InputStream> response = this.http.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                closeQuietly(response.body());
                throw new IllegalStateException("O opencode recusou o fluxo de eventos: HTTP " + response.statusCode());
            }

            return response.body();
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível abrir o fluxo de eventos do opencode", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrompido abrindo o fluxo de eventos do opencode", e);
        }
    }

    private JsonNode post(
            TocaEndpoint endpoint,
            String path,
            String directory,
            Object body
    ) {
        try {
            HttpRequest request = this.request(endpoint, path, directory)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(this.json.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = this.http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("opencode respondeu HTTP " + response.statusCode() + " em POST " + path + ": " + response.body());
            }

            return response.body().isBlank() ? this.json.createObjectNode() : this.json.readTree(response.body());
        } catch (IOException e) {
            throw new UncheckedIOException("Falha falando com o opencode em POST " + path, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrompido falando com o opencode", e);
        }
    }

    private HttpRequest.Builder request(
            TocaEndpoint endpoint,
            String path,
            String directory
    ) {
        String query = "?directory=" + URLEncoder.encode(directory, StandardCharsets.UTF_8);

        return HttpRequest
                .newBuilder(URI.create(endpoint.baseUrl() + path + query))
                .header("Authorization", basicAuth(endpoint));
    }

    private static String basicAuth(TocaEndpoint endpoint) {
        String credentials = endpoint.username() + ":" + endpoint.password();

        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException e) {
            log.debug("Erro fechando o fluxo do opencode: {}", e.getMessage());
        }
    }
}
