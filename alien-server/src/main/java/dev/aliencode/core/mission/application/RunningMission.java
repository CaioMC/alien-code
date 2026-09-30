package dev.aliencode.core.mission.application;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import dev.aliencode.core.mission.domain.model.MissionId;
import dev.aliencode.core.toca.domain.model.TocaEndpoint;

import static java.util.Objects.nonNull;

/**
 * Estado de uma missão enquanto ela é conduzida: a sessão do agente (quando já existe) e o
 * desfecho da tarefa. O primeiro desfecho vence: um pedido de parada que chega antes do
 * {@code session.idle} faz a tarefa terminar como parada, e não como concluída.
 */
class RunningMission {

    private final MissionId id;
    private final CompletableFuture<TaskOutcome> outcome = new CompletableFuture<>();

    private TocaEndpoint endpoint;
    private String directory;
    private String sessionId;
    private boolean stopRequested;

    RunningMission(MissionId id) {
        this.id = id;
    }

    MissionId id() {
        return this.id;
    }

    synchronized String sessionId() {
        return this.sessionId;
    }

    synchronized TocaEndpoint endpoint() {
        return this.endpoint;
    }

    synchronized String directory() {
        return this.directory;
    }

    /** @return {@code true} se a parada foi pedida antes da sessão existir (a sessão deve ser abortada) */
    synchronized boolean attachSession(
            TocaEndpoint endpoint,
            String directory,
            String sessionId
    ) {
        this.endpoint = endpoint;
        this.directory = directory;
        this.sessionId = sessionId;

        return this.stopRequested;
    }

    /** @return {@code true} se já há sessão do agente para abortar */
    synchronized boolean requestStop() {
        this.stopRequested = true;
        this.outcome.complete(TaskOutcome.stopped());

        return nonNull(this.sessionId);
    }

    synchronized boolean isStopRequested() {
        return this.stopRequested;
    }

    void finish(TaskOutcome result) {
        this.outcome.complete(result);
    }

    /** Espera o desfecho; ao estourar o tempo, registra TIMED_OUT (se nada chegou antes). */
    TaskOutcome await(Duration timeout) {
        try {
            return this.outcome.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            this.outcome.complete(TaskOutcome.timedOut("A tarefa passou do tempo máximo de " + timeout.toMinutes() + " min"));

            return this.outcome.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            this.outcome.complete(TaskOutcome.failed("Condução da missão interrompida"));

            return this.outcome.join();
        } catch (ExecutionException e) {
            return TaskOutcome.failed(e.getCause().getMessage());
        }
    }
}
