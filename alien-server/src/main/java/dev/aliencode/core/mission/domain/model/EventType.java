package dev.aliencode.core.mission.domain.model;

/** Tipos de evento da timeline usados no M1 (especificação, seção 8.2). */
public enum EventType {
    MISSION_CREATED("mission.created"),
    MISSION_STATE("mission.state"),
    STEP_STARTED("step.started"),
    STEP_COMPLETED("step.completed"),
    STEP_FAILED("step.failed"),
    ASSISTANT_DELTA("assistant.delta"),
    THINKING_DELTA("thinking.delta"),
    TOOL_STARTED("tool.started"),
    TOOL_COMPLETED("tool.completed"),
    TERMINAL_OUTPUT("terminal.output"),
    FILE_CHANGED("file.changed"),
    DIFF_UPDATED("diff.updated"),
    BUDGET_UPDATED("budget.updated");

    private final String wireName;

    EventType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return this.wireName;
    }

    public static EventType fromWireName(String wireName) {
        for (EventType type : values()) {
            if (type.wireName.equals(wireName)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Tipo de evento desconhecido: " + wireName);
    }
}
