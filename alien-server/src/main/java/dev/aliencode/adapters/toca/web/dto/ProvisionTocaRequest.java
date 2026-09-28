package dev.aliencode.adapters.toca.web.dto;

import java.nio.file.Path;
import java.util.List;

import dev.aliencode.core.toca.domain.RepositorySeed;
import dev.aliencode.core.toca.domain.Seed;
import dev.aliencode.core.toca.usecase.ProvisionTocaCommand;

/**
 * Corpo de {@code POST /api/tocas}. Exemplos:
 * <pre>
 * {"seed": {"type": "existing", "repositories": [{"name": "api", "path": "/home/dev/api", "ref": "main"}]}}
 * {"seed": {"type": "new", "name": "meu-projeto"}}
 * </pre>
 */
public record ProvisionTocaRequest(String missionId, SeedRequest seed) {

    public record SeedRequest(String type, String name, List<RepositoryRequest> repositories) {
    }

    public record RepositoryRequest(String name, String path, String ref) {
    }

    public ProvisionTocaCommand toCommand() {
        if (this.seed == null || this.seed.type() == null) {
            throw new IllegalArgumentException("Informe seed.type: 'existing' ou 'new'");
        }
        Seed domainSeed = switch (this.seed.type()) {
            case "existing" -> new Seed.ExistingRepositories(repositories());
            case "new" -> new Seed.NewProject(this.seed.name());
            default -> throw new IllegalArgumentException("seed.type desconhecido: " + this.seed.type());
        };
        return new ProvisionTocaCommand(this.missionId, domainSeed);
    }

    private List<RepositorySeed> repositories() {
        if (this.seed.repositories() == null) {
            return List.of();
        }
        return this.seed.repositories().stream()
                .map(r -> {
                    if (r.path() == null || r.path().isBlank()) {
                        throw new IllegalArgumentException("O repositório '" + r.name() + "' precisa de path");
                    }
                    String name = r.name() != null ? r.name() : Path.of(r.path()).getFileName().toString();
                    return new RepositorySeed(name, Path.of(r.path()), r.ref());
                })
                .toList();
    }
}
