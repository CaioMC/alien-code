package dev.aliencode.core.mission.application;

/** Como a tarefa terminou, do ponto de vista do orquestrador. */
record TaskOutcome(
        Kind kind,
        String message
) {

    enum Kind {
        COMPLETED,
        FAILED,
        STOPPED,
        TIMED_OUT
    }

    static TaskOutcome completed() {
        return new TaskOutcome(Kind.COMPLETED, null);
    }

    static TaskOutcome failed(String message) {
        return new TaskOutcome(Kind.FAILED, message);
    }

    static TaskOutcome stopped() {
        return new TaskOutcome(Kind.STOPPED, "Parada pelo usuário");
    }

    static TaskOutcome timedOut(String message) {
        return new TaskOutcome(Kind.TIMED_OUT, message);
    }
}
