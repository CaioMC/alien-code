package dev.aliencode.adapters.mission.web.request;

import dev.aliencode.adapters.toca.web.request.SeedRequest;

/**
 * Corpo de {@code POST /api/missions}. Exemplo:
 * <pre>
 * {"prompt": "Corrija a soma em calc.py",
 *  "seed": {"type": "existing", "repositories": [{"path": "/home/dev/calc"}]},
 *  "model": "ollama/qwen3:8b"}
 * </pre>
 *
 * @param title opcional; sem título, a primeira linha do prompt
 * @param model opcional; sem modelo, o padrão do servidor
 */
public record StartMissionRequest(
        String title,
        String prompt,
        String model,
        SeedRequest seed
) {
}
