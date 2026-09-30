package dev.aliencode.core.mission.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.aliencode.core.mission.domain.model.EventSource;
import dev.aliencode.core.mission.domain.model.EventType;
import dev.aliencode.core.mission.domain.model.NewEvent;
import dev.aliencode.core.mission.port.agent.AgentEvent;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * Traduz os eventos do agente de uma tarefa para linhas da timeline (especificação, seção 8.3):
 * cada tool call vira um passo filho da tarefa, e comandos também alimentam o terminal.
 * Guarda o que precisa entre eventos: tools ainda abertas e tokens acumulados.
 */
class TaskTimeline {

    /** Saídas maiores que isso são cortadas no evento; o blob completo chega com o M2. */
    static final int MAX_OUTPUT = 16_000;

    private final String taskStepId;

    private final Map<String, String> openTools = new LinkedHashMap<>();
    private long inputTokens;
    private long outputTokens;
    private long reasoningTokens;

    TaskTimeline(String taskStepId) {
        this.taskStepId = taskStepId;
    }

    String taskStepId() {
        return this.taskStepId;
    }

    List<NewEvent> translate(AgentEvent event) {
        return switch (event) {
            case AgentEvent.TextDelta delta -> List.of(this.opencode(EventType.ASSISTANT_DELTA, this.child("text", delta.partId()), Map.of("text", delta.text())));
            case AgentEvent.ReasoningDelta delta -> List.of(this.opencode(EventType.THINKING_DELTA, this.child("thinking", delta.partId()), Map.of("text", delta.text())));
            case AgentEvent.ToolStarted started -> this.toolStarted(started);
            case AgentEvent.ToolFinished finished -> this.toolFinished(finished);
            case AgentEvent.FileChanged changed -> List.of(this.fileChanged(changed));
            case AgentEvent.ModelCallFinished call -> List.of(this.budget(call));
            case AgentEvent.SessionIdle idle -> List.of();
            case AgentEvent.SessionFailed failed -> List.of();
        };
    }

    /** Fecha as tools que ficaram abertas (parada, erro, tempo esgotado). */
    List<NewEvent> closeOpenTools(String reason) {
        List<NewEvent> events = new ArrayList<>();

        this.openTools.forEach((callId, tool) -> {
            Map<String, Object> payload = new LinkedHashMap<>();

            payload.put("tool", tool);
            payload.put("status", "cancelled");
            payload.put("output", reason);

            events.add(this.opencode(EventType.TOOL_COMPLETED, this.child("tool", callId), payload));
        });

        this.openTools.clear();

        return events;
    }

    private List<NewEvent> toolStarted(AgentEvent.ToolStarted started) {
        if (this.openTools.containsKey(started.callId())) {
            return List.of();
        }

        this.openTools.put(started.callId(), started.tool());

        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put("tool", started.tool());
        payload.put("title", started.title());
        payload.put("input", started.input());

        return List.of(this.opencode(EventType.TOOL_STARTED, this.child("tool", started.callId()), payload));
    }

    private List<NewEvent> toolFinished(AgentEvent.ToolFinished finished) {
        this.openTools.remove(finished.callId());

        String stepId = this.child("tool", finished.callId());
        String output = truncate(finished.output());
        boolean truncated = nonNull(finished.output()) && finished.output().length() > MAX_OUTPUT;

        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put("tool", finished.tool());
        payload.put("status", finished.success() ? "completed" : "failed");
        payload.put("title", finished.title());
        payload.put("exitCode", finished.exitCode());
        payload.put("durationMs", finished.durationMs());
        payload.put("output", output);
        payload.put("truncated", truncated);

        List<NewEvent> events = new ArrayList<>();

        events.add(this.opencode(EventType.TOOL_COMPLETED, stepId, payload));

        if ("bash".equals(finished.tool())) {
            Map<String, Object> terminal = new LinkedHashMap<>();

            terminal.put("command", finished.title());
            terminal.put("output", output);
            terminal.put("exitCode", finished.exitCode());

            events.add(this.opencode(EventType.TERMINAL_OUTPUT, stepId, terminal));
        }

        return events;
    }

    private NewEvent fileChanged(AgentEvent.FileChanged changed) {
        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put("path", changed.path());
        payload.put("patch", truncate(changed.patch()));
        payload.put("additions", changed.additions());
        payload.put("deletions", changed.deletions());

        return this.opencode(EventType.FILE_CHANGED, this.child("file", changed.path()), payload);
    }

    private NewEvent budget(AgentEvent.ModelCallFinished call) {
        this.inputTokens += call.inputTokens();
        this.outputTokens += call.outputTokens();
        this.reasoningTokens += call.reasoningTokens();

        Map<String, Object> payload = new LinkedHashMap<>();

        payload.put("inputTokens", this.inputTokens);
        payload.put("outputTokens", this.outputTokens);
        payload.put("reasoningTokens", this.reasoningTokens);
        payload.put("totalTokens", this.inputTokens + this.outputTokens + this.reasoningTokens);

        return new NewEvent(
                EventType.BUDGET_UPDATED,
                null,
                null,
                EventSource.OPENCODE,
                payload
        );
    }

    private NewEvent opencode(
            EventType type,
            String stepId,
            Map<String, Object> payload
    ) {
        return new NewEvent(
                type,
                stepId,
                this.taskStepId,
                EventSource.OPENCODE,
                payload
        );
    }

    private String child(
            String kind,
            String id
    ) {
        return this.taskStepId + "." + kind + "." + id;
    }

    private static String truncate(String text) {
        if (isNull(text) || text.length() <= MAX_OUTPUT) {
            return text;
        }

        return text.substring(0, MAX_OUTPUT);
    }
}
