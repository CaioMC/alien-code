package dev.aliencode.adapters.mission.opencode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.aliencode.core.mission.port.agent.AgentEvent;

import static java.util.Objects.isNull;

/**
 * Traduz eventos do SSE {@code GET /event} do opencode 1.18.33 para {@link AgentEvent}.
 *
 * <p>O servidor emite a geração {@code message.part.*} (e não {@code session.next.*}, que também
 * existe no OpenAPI): {@code message.part.updated} traz o estado de cada parte (texto, raciocínio,
 * tool, step-finish) e {@code message.part.delta} traz o texto em streaming, só com o id da parte.
 * Por isso o tradutor lembra o tipo de cada parte. Um tradutor por assinatura; não é thread-safe.
 * Fluxos reais gravados: {@code src/test/resources/opencode/*.sse}.
 */
class OpencodeEventTranslator {

    static final String ABORTED_ERROR = "MessageAbortedError";

    private static final TypeReference<Map<String, Object>> INPUT_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper json;

    private final Map<String, String> partTypes = new HashMap<>();
    private final Set<String> startedTools = new HashSet<>();
    private final Set<String> finishedTools = new HashSet<>();

    OpencodeEventTranslator(ObjectMapper json) {
        this.json = json;
    }

    List<AgentEvent> translate(JsonNode event) {
        JsonNode properties = event.path("properties");
        String sessionId = properties.path("sessionID").asText(null);

        if (isNull(sessionId)) {
            return List.of();
        }

        return switch (event.path("type").asText()) {
            case "message.part.updated" -> this.partUpdated(sessionId, properties.path("part"));
            case "message.part.delta" -> this.partDelta(sessionId, properties);
            case "session.idle" -> List.of(new AgentEvent.SessionIdle(sessionId));
            case "session.error" -> List.of(this.sessionError(sessionId, properties.path("error")));
            default -> List.of();
        };
    }

    private List<AgentEvent> partUpdated(
            String sessionId,
            JsonNode part
    ) {
        String type = part.path("type").asText();

        this.partTypes.put(part.path("id").asText(), type);

        return switch (type) {
            case "tool" -> this.tool(sessionId, part);
            case "step-finish" -> List.of(this.stepFinished(sessionId, part.path("tokens")));
            default -> List.of();
        };
    }

    private List<AgentEvent> partDelta(
            String sessionId,
            JsonNode properties
    ) {
        if (!"text".equals(properties.path("field").asText())) {
            return List.of();
        }

        String partId = properties.path("partID").asText();
        String text = properties.path("delta").asText("");

        if ("reasoning".equals(this.partTypes.get(partId))) {
            return List.of(new AgentEvent.ReasoningDelta(sessionId, partId, text));
        }

        return List.of(new AgentEvent.TextDelta(sessionId, partId, text));
    }

    /** Estados de uma tool: pending (sem input) → running → completed | error. */
    private List<AgentEvent> tool(
            String sessionId,
            JsonNode part
    ) {
        String callId = part.path("callID").asText();
        String tool = part.path("tool").asText();
        JsonNode state = part.path("state");
        String status = state.path("status").asText();

        if ("pending".equals(status) || this.finishedTools.contains(callId)) {
            return List.of();
        }

        List<AgentEvent> events = new ArrayList<>();

        if (this.startedTools.add(callId)) {
            events.add(
                    new AgentEvent.ToolStarted(
                            sessionId,
                            callId,
                            tool,
                            this.titleOf(tool, state),
                            this.inputOf(state)
                    )
            );
        }

        if ("completed".equals(status) || "error".equals(status)) {
            this.finishedTools.add(callId);
            events.add(
                    this.toolFinished(
                            sessionId,
                            callId,
                            tool,
                            state
                    )
            );
            events.addAll(this.fileChanges(sessionId, state));
        }

        return events;
    }

    private AgentEvent toolFinished(
            String sessionId,
            String callId,
            String tool,
            JsonNode state
    ) {
        boolean success = "completed".equals(state.path("status").asText());
        JsonNode exit = state.path("metadata").path("exit");
        JsonNode time = state.path("time");

        return new AgentEvent.ToolFinished(
                sessionId,
                callId,
                tool,
                success,
                this.titleOf(tool, state),
                success ? state.path("output").asText("") : state.path("error").asText(""),
                exit.isNumber() ? exit.asInt() : null,
                time.has("end") ? time.path("end").asLong() - time.path("start").asLong() : null
        );
    }

    /** Ferramentas de edição trazem o diff do arquivo em {@code metadata.filediff}. */
    private List<AgentEvent> fileChanges(
            String sessionId,
            JsonNode state
    ) {
        JsonNode diff = state.path("metadata").path("filediff");

        if (!diff.has("file")) {
            return List.of();
        }

        return List.of(new AgentEvent.FileChanged(
                sessionId,
                diff.path("file").asText(),
                diff.path("patch").asText(""),
                diff.path("additions").asInt(),
                diff.path("deletions").asInt()
        ));
    }

    private AgentEvent stepFinished(
            String sessionId,
            JsonNode tokens
    ) {
        return new AgentEvent.ModelCallFinished(
                sessionId,
                tokens.path("input").asLong(),
                tokens.path("output").asLong(),
                tokens.path("reasoning").asLong()
        );
    }

    private AgentEvent sessionError(
            String sessionId,
            JsonNode error
    ) {
        String name = error.path("name").asText("Erro");
        String message = error.path("data").path("message").asText(name);

        return new AgentEvent.SessionFailed(sessionId, message, ABORTED_ERROR.equals(name));
    }

    /** O título que o opencode calcula só chega no fim; até lá, o argumento principal da tool. */
    private String titleOf(
            String tool,
            JsonNode state
    ) {
        if (state.hasNonNull("title")) {
            return state.path("title").asText();
        }

        JsonNode input = state.path("input");

        return switch (tool) {
            case "bash" -> input.path("command").asText(tool);
            case "read", "edit", "write" -> input.path("filePath").asText(tool);
            default -> tool;
        };
    }

    private Map<String, Object> inputOf(JsonNode state) {
        JsonNode input = state.path("input");

        return input.isObject() ? this.json.convertValue(input, INPUT_TYPE) : Map.of();
    }
}
