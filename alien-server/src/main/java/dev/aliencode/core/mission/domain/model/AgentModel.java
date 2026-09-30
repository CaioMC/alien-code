package dev.aliencode.core.mission.domain.model;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Modelo que o agente usa numa missão, no formato do opencode: {@code provedor/modelo}
 * (ex.: {@code ollama/qwen3:8b}).
 */
public record AgentModel(
        String providerId,
        String modelId
) {

    public AgentModel {
        if (isBlank(providerId) || isBlank(modelId)) {
            throw new IllegalArgumentException("Informe o modelo como provedor/modelo (ex.: ollama/qwen3:8b)");
        }
    }

    public static AgentModel parse(String qualified) {
        if (isBlank(qualified) || !qualified.contains("/")) {
            throw new IllegalArgumentException("Modelo inválido: '" + qualified + "' (use provedor/modelo, ex.: ollama/qwen3:8b)");
        }

        int slash = qualified.indexOf('/');

        return new AgentModel(qualified.substring(0, slash), qualified.substring(slash + 1));
    }

    @Override
    public String toString() {
        return this.providerId + "/" + this.modelId;
    }
}
