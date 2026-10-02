package dev.aliencode.core.mission.application;

import java.time.Duration;

import dev.aliencode.core.mission.domain.model.AgentModel;

import static java.util.Objects.isNull;

/**
 * Configuração das missões, já sem nada de Spring.
 *
 * @param taskTimeout tempo de parede máximo de uma tarefa; ao estourar, a sessão é abortada
 * @param keepToca    modo debug: não descarta a Toca ao fim da missão (o TTL ainda vale)
 */
public record MissionSettings(
        AgentModel defaultModel,
        Duration taskTimeout,
        boolean keepToca
) {

    public MissionSettings {
        if (isNull(defaultModel)) {
            throw new IllegalArgumentException("Configure o modelo padrão (alien.model.default)");
        }

        if (isNull(taskTimeout) || taskTimeout.isNegative() || taskTimeout.isZero()) {
            throw new IllegalArgumentException("O tempo máximo da tarefa precisa ser positivo (alien.mission.task-timeout)");
        }
    }
}
