package dev.aliencode.adapters.toca.web.request;

/**
 * Corpo de {@code POST /api/tocas}. Exemplos:
 * <pre>
 * {"missionId": "m-1", "seed": {"type": "existing", "repositories": [{"name": "api", "path": "/home/dev/api", "ref": "main"}]}}
 * {"seed": {"type": "new", "name": "meu-projeto"}}
 * </pre>
 */
public record ProvisionTocaRequest(
        String missionId,
        SeedRequest seed
) {
}