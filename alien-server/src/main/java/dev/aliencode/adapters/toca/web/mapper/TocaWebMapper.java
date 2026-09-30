package dev.aliencode.adapters.toca.web.mapper;

import java.nio.file.Path;
import java.util.List;

import org.springframework.stereotype.Component;

import dev.aliencode.adapters.toca.web.request.ProvisionTocaRequest;
import dev.aliencode.adapters.toca.web.request.RepositoryRequest;
import dev.aliencode.adapters.toca.web.request.SeedRequest;
import dev.aliencode.adapters.toca.web.response.TocaResponse;
import dev.aliencode.core.toca.domain.model.RepositorySeed;
import dev.aliencode.core.toca.domain.model.Seed;
import dev.aliencode.core.toca.domain.model.Toca;
import dev.aliencode.core.toca.usecase.command.ProvisionTocaCommand;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * Tradução entre o formato HTTP e o domínio. Os DTOs são só dados; toda a conversão
 * (e a validação de formato que ela exige) mora aqui, e as regras de negócio ficam no domínio.
 */
@Component
public class TocaWebMapper {

    private static final int SHORT_CONTAINER_ID = 12;

    public ProvisionTocaCommand toCommand(ProvisionTocaRequest request) {
        if (isNull(request) || isNull(request.seed()) || isNull(request.seed().type())) {
            throw new IllegalArgumentException("Informe seed.type: '" + SeedRequest.EXISTING + "' ou '" + SeedRequest.NEW + "'");
        }

        return new ProvisionTocaCommand(request.missionId(), this.toSeed(request.seed()));
    }

    public TocaResponse toResponse(Toca toca) {
        return new TocaResponse(
                toca.id().value(),
                toca.missionId(),
                toca.status().name(),
                this.shortContainerId(toca.containerId()),
                isNull(toca.endpoint()) ? null : toca.endpoint().baseUrl().toString(),
                toca.workspaceDirs(),
                toca.createdAt(),
                toca.expiresAt(),
                toca.failureReason()
        );
    }

    public List<TocaResponse> toResponses(List<Toca> tocas) {
        return tocas.stream().map(this::toResponse).toList();
    }

    private Seed toSeed(SeedRequest seed) {
        return switch (seed.type()) {
            case SeedRequest.EXISTING -> new Seed.ExistingRepositories(this.toRepositories(seed.repositories()));
            case SeedRequest.NEW -> new Seed.NewProject(seed.name());
            default -> throw new IllegalArgumentException("seed.type desconhecido: " + seed.type());
        };
    }

    private List<RepositorySeed> toRepositories(List<RepositoryRequest> repositories) {
        if (isNull(repositories)) {
            return List.of();
        }

        return repositories.stream().map(this::toRepository).toList();
    }

    private RepositorySeed toRepository(RepositoryRequest repository) {
        if (isNull(repository.path()) || repository.path().isBlank()) {
            throw new IllegalArgumentException("O repositório '" + repository.name() + "' precisa de path");
        }

        Path path = Path.of(repository.path());
        String name = nonNull(repository.name()) ? repository.name() : String.valueOf(path.getFileName());

        return new RepositorySeed(name, path, repository.ref());
    }

    private String shortContainerId(String containerId) {
        if (isNull(containerId)) {
            return null;
        }

        return containerId.substring(
                0,
                Math.min(SHORT_CONTAINER_ID, containerId.length())
        );
    }
}
