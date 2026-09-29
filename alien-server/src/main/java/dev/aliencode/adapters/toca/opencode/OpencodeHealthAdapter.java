package dev.aliencode.adapters.toca.opencode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.aliencode.core.toca.domain.model.TocaEndpoint;
import dev.aliencode.core.toca.port.harness.AgentHarnessPort;
import dev.aliencode.core.toca.port.harness.HarnessNotReadyException;

/**
 * Espera o {@code opencode serve} da Toca responder em {@code GET /global/health}
 * (basic auth, resposta {@code {"healthy":true,"version":"..."}}).
 */
@Component
public class OpencodeHealthAdapter implements AgentHarnessPort {

    private static final Duration POLL_INTERVAL = Duration.ofMillis(250);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper json;

    public OpencodeHealthAdapter(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public String awaitReady(TocaEndpoint endpoint, Duration timeout) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint.baseUrl() + "/global/health"))
                .header("Authorization", basicAuth(endpoint))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();

        Instant deadline = Instant.now().plus(timeout);
        String lastProblem = "sem resposta";
        while (Instant.now().isBefore(deadline)) {
            try {
                HttpResponse<String> response = this.http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonNode body = this.json.readTree(response.body());
                    if (body.path("healthy").asBoolean(false)) {
                        return body.path("version").asText("desconhecida");
                    }
                    lastProblem = "healthy=false";
                } else {
                    lastProblem = "HTTP " + response.statusCode();
                }
            } catch (IOException e) {
                lastProblem = e.getClass().getSimpleName();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new HarnessNotReadyException("Interrompido esperando o opencode");
            }
            sleep();
        }
        throw new HarnessNotReadyException("O opencode não respondeu em " + timeout.toSeconds() + "s em "
                + endpoint.baseUrl() + " (último problema: " + lastProblem + ")");
    }

    private static String basicAuth(TocaEndpoint endpoint) {
        String credentials = endpoint.username() + ":" + endpoint.password();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private static void sleep() {
        try {
            Thread.sleep(POLL_INTERVAL);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HarnessNotReadyException("Interrompido esperando o opencode");
        }
    }
}
