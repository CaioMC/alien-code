package dev.aliencode.it;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * LLM falso compatível com {@code POST /v1/chat/completions} (streaming), no lugar do Ollama:
 * o opencode de verdade, dentro de uma Toca de verdade, recebe tool calls roteirizadas e executa.
 * Assim o teste é rápido e determinístico, sem depender de modelo nem de GPU.
 *
 * <p>Roteiro: pedido com "dorme" → {@code bash sleep 120} (para testar o Parar); qualquer outro →
 * ler calc.py, corrigir a soma, rodar o teste e responder. Pedidos sem tools (título da sessão)
 * recebem só texto.
 */
final class ScriptedLlmServer implements AutoCloseable {

    private static final String FILE = "/workspace/calc/calc.py";

    private final ObjectMapper json = new ObjectMapper();
    private final HttpServer server;

    ScriptedLlmServer(String host) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(host, 0), 0);
        this.server.createContext("/v1/chat/completions", this::complete);
        this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        this.server.start();
    }

    String baseUrl() {
        return "http://" + this.server.getAddress().getHostString() + ":" + this.server.getAddress().getPort() + "/v1";
    }

    @Override
    public void close() {
        this.server.stop(0);
    }

    private void complete(HttpExchange exchange) throws IOException {
        JsonNode request = this.json.readTree(exchange.getRequestBody());
        List<Map<String, Object>> chunks = this.chunksFor(request);

        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);

        try (OutputStream out = exchange.getResponseBody()) {
            for (Map<String, Object> chunk : chunks) {
                out.write(("data: " + this.json.writeValueAsString(chunk) + "\n\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }

            out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
        }
    }

    private List<Map<String, Object>> chunksFor(JsonNode request) {
        String model = request.path("model").asText();

        if (!request.has("tools")) {
            return this.text(model, "Missão de teste");
        }

        int turn = this.assistantTurns(request.path("messages"));

        if (this.lastUserMessage(request.path("messages")).contains("dorme")) {
            return turn == 0 ? this.tool(model, turn, "bash", Map.of("command", "sleep 120", "description", "Espera")) : this.text(model, "ok");
        }

        return switch (turn) {
            case 0 -> this.tool(model, turn, "read", Map.of("filePath", FILE));
            case 1 -> this.tool(model, turn, "edit", Map.of("filePath", FILE, "oldString", "return a - b", "newString", "return a + b"));
            case 2 -> this.tool(model, turn, "bash", Map.of("command", "cd /workspace/calc && python3 -c 'from calc import soma; print(soma(2, 3))'", "description", "Testa a soma"));
            default -> this.text(model, "Corrigido: soma devolve a + b.");
        };
    }

    private int assistantTurns(JsonNode messages) {
        int lastUser = 0;

        for (int i = 0; i < messages.size(); i++) {
            if ("user".equals(messages.get(i).path("role").asText())) {
                lastUser = i;
            }
        }

        int turns = 0;

        for (int i = lastUser; i < messages.size(); i++) {
            if ("assistant".equals(messages.get(i).path("role").asText())) {
                turns++;
            }
        }

        return turns;
    }

    private String lastUserMessage(JsonNode messages) {
        String last = "";

        for (JsonNode message : messages) {
            if ("user".equals(message.path("role").asText())) {
                last = message.path("content").toString();
            }
        }

        return last;
    }

    private List<Map<String, Object>> text(
            String model,
            String text
    ) {
        List<Map<String, Object>> chunks = new ArrayList<>();

        chunks.add(this.chunk(model, Map.of("role", "assistant"), null));
        chunks.add(this.chunk(model, Map.of("reasoning_content", "Pensando. "), null));

        for (String word : text.split(" ")) {
            chunks.add(this.chunk(model, Map.of("content", word + " "), null));
        }

        chunks.add(this.chunk(model, Map.of(), "stop"));
        chunks.add(this.usage(model));

        return chunks;
    }

    private List<Map<String, Object>> tool(
            String model,
            int turn,
            String name,
            Map<String, Object> arguments
    ) {
        String raw = this.writeJson(arguments);
        Map<String, Object> call = Map.of("index", 0, "id", "call_" + turn, "type", "function", "function", Map.of("name", name, "arguments", raw));

        return List.of(
                this.chunk(model, Map.of("role", "assistant"), null),
                this.chunk(model, Map.of("tool_calls", List.of(call)), null),
                this.chunk(model, Map.of(), "tool_calls"),
                this.usage(model)
        );
    }

    private Map<String, Object> chunk(
            String model,
            Map<String, Object> delta,
            String finishReason
    ) {
        Map<String, Object> choice = new HashMap<>();

        choice.put("index", 0);
        choice.put("delta", delta);
        choice.put("finish_reason", finishReason);

        return Map.of("id", "c1", "object", "chat.completion.chunk", "created", 0, "model", model, "choices", List.of(choice));
    }

    private Map<String, Object> usage(String model) {
        Map<String, Object> usage = Map.of("prompt_tokens", 1000, "completion_tokens", 20, "total_tokens", 1020);

        return Map.of("id", "c1", "object", "chat.completion.chunk", "created", 0, "model", model, "choices", List.of(), "usage", usage);
    }

    private String writeJson(Object value) {
        try {
            return this.json.writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
