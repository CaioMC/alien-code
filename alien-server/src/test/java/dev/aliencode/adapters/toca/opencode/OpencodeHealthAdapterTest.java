package dev.aliencode.adapters.toca.opencode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import dev.aliencode.core.toca.domain.model.TocaEndpoint;
import dev.aliencode.core.toca.port.harness.HarnessNotReadyException;

class OpencodeHealthAdapterTest {

    private static final String EXPECTED_AUTH = "Basic "
            + Base64.getEncoder().encodeToString("opencode:s3nha".getBytes(StandardCharsets.UTF_8));

    private HttpServer server;
    private final AtomicInteger calls = new AtomicInteger();
    private volatile int unhealthyCalls;

    private final OpencodeHealthAdapter adapter = new OpencodeHealthAdapter(new ObjectMapper());

    @BeforeEach
    void startFakeOpencode() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.createContext("/global/health", this::health);
        this.server.start();
    }

    @AfterEach
    void stop() {
        this.server.stop(0);
    }

    @Test
    void esperaAteOOpencodeFicarSaudavel() {
        this.unhealthyCalls = 3;

        String version = this.adapter.awaitReady(this.endpoint("s3nha"), Duration.ofSeconds(5));

        assertThat(version).isEqualTo("1.18.33");
        assertThat(this.calls.get()).isEqualTo(4);
    }

    @Test
    void senhaErradaEsgotaOTempoInformandoOStatus() {
        assertThatThrownBy(() -> this.adapter.awaitReady(this.endpoint("errada"), Duration.ofMillis(600)))
                .isInstanceOf(HarnessNotReadyException.class)
                .hasMessageContaining("HTTP 401");
    }

    @Test
    void servidorForaDoArEsgotaOTempo() {
        this.server.stop(0);

        assertThatThrownBy(() -> this.adapter.awaitReady(this.endpoint("s3nha"), Duration.ofMillis(600)))
                .isInstanceOf(HarnessNotReadyException.class)
                .hasMessageContaining("não respondeu");
    }

    private TocaEndpoint endpoint(String password) {
        URI base = URI.create("http://127.0.0.1:" + this.server.getAddress().getPort());
        return new TocaEndpoint(base, "opencode", password);
    }

    private void health(HttpExchange exchange) throws IOException {
        this.calls.incrementAndGet();
        if (!EXPECTED_AUTH.equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            exchange.sendResponseHeaders(401, -1);
        } else if (this.unhealthyCalls > 0) {
            this.unhealthyCalls--;
            exchange.sendResponseHeaders(503, -1);
        } else {
            byte[] body = "{\"healthy\":true,\"version\":\"1.18.33\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }
}
